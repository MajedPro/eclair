package fr.acinq.eclair.blockchain

import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin.Script._
import fr.acinq.bitcoin._
import fr.acinq.eclair.blockchain.rpc.{BitcoinJsonRPCClient, JsonRPCError}
import fr.acinq.eclair.transactions.{Scripts, Transactions}
import org.bouncycastle.util.encoders.Hex
import org.json4s.JsonAST._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by PM on 26/04/2016.
  */
class ExtendedBitcoinClient(val client: BitcoinJsonRPCClient) {

  import ExtendedBitcoinClient._

  implicit val formats = org.json4s.DefaultFormats

  // TODO: this will probably not be needed once segwit is merged into core
  val protocolVersion = Protocol.PROTOCOL_VERSION

  def tx2Hex(tx: Transaction): String = Hex.toHexString(Transaction.write(tx, protocolVersion))

  def hex2tx(hex: String): Transaction = Transaction.read(hex, protocolVersion)

  def getTxConfirmations(txId: String)(implicit ec: ExecutionContext): Future[Option[Int]] =
    client.invoke("getrawtransaction", txId, 1) // we choose verbose output to get the number of confirmations
      .map(json => Some((json \ "confirmations").extractOrElse[Int](0)))
      .recover {
        case t: JsonRPCError if t.error.code == -5 => None
      }

  def getTxBlockHash(txId: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    client.invoke("getrawtransaction", txId, 1) // we choose verbose output to get the number of confirmations
      .map(json => Some((json \ "blockhash").extract[String]))
      .recover {
        case t: JsonRPCError if t.error.code == -5 => None
      }

  def getTxsSinceBlockHash(blockHash: String, previous: Seq[Transaction] = Nil)(implicit ec: ExecutionContext): Future[Seq[Transaction]] =
    for {
      (nextblockhash_opt, txids) <- client.invoke("getblock", blockHash).map(json => ((json \ "nextblockhash").extractOpt[String], (json \ "tx").extract[List[String]]))
      next <- Future.sequence(txids.map(getTransaction(_)))
      res <- nextblockhash_opt match {
        case Some(nextBlockHash) => getTxsSinceBlockHash(nextBlockHash, previous ++ next)
        case None => Future.successful(previous ++ next)
      }
    } yield res


  /**
    * *used in interop test*
    * tell bitcoind to sent bitcoins from a specific local account
    *
    * @param account     name of the local account to send bitcoins from
    * @param destination destination address
    * @param amount      amount in BTC (not milliBTC, not Satoshis !!)
    * @param ec          execution context
    * @return a Future[txid] where txid (a String) is the is of the tx that sends the bitcoins
    */
  def sendFromAccount(account: String, destination: String, amount: Double)(implicit ec: ExecutionContext): Future[String] =
    client.invoke("sendfrom", account, destination, amount) collect {
      case JString(txid) => txid
    }

  /**
    * @param txId
    * @param ec
    * @return
    */
  def getRawTransaction(txId: String)(implicit ec: ExecutionContext): Future[String] =
    client.invoke("getrawtransaction", txId) collect {
      case JString(raw) => raw
    }

  def getTransaction(txId: String)(implicit ec: ExecutionContext): Future[Transaction] =
    getRawTransaction(txId).map(raw => Transaction.read(raw))

  def getTransaction(height: Int, index: Int)(implicit ec: ExecutionContext): Future[Transaction] =
    for {
      hash <- client.invoke("getblockhash", height).map(json => json.extract[String])
      json <- client.invoke("getblock", hash)
      JArray(txs) = json \ "tx"
      txid = txs(index).extract[String]
      tx <- getTransaction(txid)
    } yield tx

  def isTransactionOuputSpendable(txId: String, ouputIndex: Int, includeMempool: Boolean)(implicit ec: ExecutionContext): Future[Boolean] =
    for {
      json <- client.invoke("gettxout", txId, ouputIndex, includeMempool)
    } yield json != JNull


  /**
    *
    * @param txId transaction id
    * @param ec
    * @return a Future[height, index] where height is the height of the block where this transaction was published, and index is
    *         the index of the transaction in that block
    */
  def getTransactionShortId(txId: String)(implicit ec: ExecutionContext): Future[(Int, Int)] = {
    val future = for {
      Some(blockHash) <- getTxBlockHash(txId)
      json <- client.invoke("getblock", blockHash)
      JInt(height) = json \ "height"
      JString(hash) = json \ "hash"
      JArray(txs) = json \ "tx"
      index = txs.indexOf(JString(txId))
    } yield (height.toInt, index)

    future
  }

  def fundTransaction(hex: String)(implicit ec: ExecutionContext): Future[FundTransactionResponse] = {
    client.invoke("fundrawtransaction", hex /*hex.take(4) + "0000" + hex.drop(4)*/).map(json => {
      val JString(hex) = json \ "hex"
      val JInt(changepos) = json \ "changepos"
      val JDouble(fee) = json \ "fee"
      FundTransactionResponse(Transaction.read(hex), changepos.intValue(), fee)
    })
  }

  def fundTransaction(tx: Transaction)(implicit ec: ExecutionContext): Future[FundTransactionResponse] =
    fundTransaction(tx2Hex(tx))

  def signTransaction(hex: String)(implicit ec: ExecutionContext): Future[SignTransactionResponse] =
    client.invoke("signrawtransaction", hex).map(json => {
      val JString(hex) = json \ "hex"
      val JBool(complete) = json \ "complete"
      SignTransactionResponse(Transaction.read(hex), complete)
    })

  def signTransaction(tx: Transaction)(implicit ec: ExecutionContext): Future[SignTransactionResponse] =
    signTransaction(tx2Hex(tx))

  def publishTransaction(hex: String)(implicit ec: ExecutionContext): Future[String] =
    client.invoke("sendrawtransaction", hex) collect {
      case JString(txid) => txid
    }

  def publishTransaction(tx: Transaction)(implicit ec: ExecutionContext): Future[String] =
    publishTransaction(tx2Hex(tx))

  def makeFundingTx(localFundingPubkey: PublicKey, remoteFundingPubkey: PublicKey, amount: Satoshi, fee: Satoshi)(implicit ec: ExecutionContext): Future[(Transaction, Transaction, Int)] = {
    // this is the funding tx that we want to publish
    val (partialTx, pubkeyScript) = Transactions.makePartialFundingTx(amount, localFundingPubkey, remoteFundingPubkey)

    val future = for {
    // ask for a new address and the corresponding private key
      JString(address) <- client.invoke("getnewaddress")
      JString(wif) <- client.invoke("dumpprivkey", address)
      priv = PrivateKey.fromBase58(wif, Base58.Prefix.SecretKeyTestnet)
      pub = priv.publicKey
      // create a tx that sends money to a WPKH output that matches our private key
      tx = Transaction(version = 2, txIn = Nil, txOut = TxOut(amount + fee, Script.pay2wpkh(pub)) :: Nil, lockTime = 0L)
      FundTransactionResponse(tx1, changePos, fee) <- fundTransaction(tx)
      // this is the first tx that we will publish, a standard tx which send money to our p2wpkh address
      SignTransactionResponse(tx2, true) <- signTransaction(tx1)
      pos = Transactions.findPubKeyScriptIndex(tx2, Script.pay2wpkh(pub))
      // now we update our funding tx to spend from our segwit tx
      tx3 = partialTx.copy(txIn = TxIn(OutPoint(tx2, pos), sequence = TxIn.SEQUENCE_FINAL, signatureScript = Nil) :: Nil)
      sig = Transaction.signInput(tx3, 0, Script.pay2pkh(pub), SIGHASH_ALL, tx2.txOut(pos).amount, SigVersion.SIGVERSION_WITNESS_V0, priv)
      tx4 = tx3.updateWitness(0, ScriptWitness(sig :: pub.toBin :: Nil))
      _ = Transaction.correctlySpends(tx4, tx2 :: Nil, ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)
    } yield (tx2, tx4, 0)

    future
  }

  /**
    * We need this to compute absolute timeouts expressed in number of blocks (where getBlockCount would be equivalent
    * to time.now())
    *
    * @param ec
    * @return the current number of blocks in the active chain
    */
  def getBlockCount(implicit ec: ExecutionContext): Future[Long] =
    client.invoke("getblockcount") collect {
      case JInt(count) => count.toLong
    }

  /**
    * We need this to keep commitment tx fees in sync with the state of the network
    *
    * @param nBlocks number of blocks until tx is confirmed
    * @param ec
    * @return the current
    */
  def estimateSmartFee(nBlocks: Int)(implicit ec: ExecutionContext): Future[Long] =
    client.invoke("estimatesmartfee", nBlocks).map(json => {
      json \ "feerate" match {
        case JDouble(feerate) => Btc(feerate).toLong
        case JInt(feerate) if feerate.toLong < 0 => feerate.toLong
        case JInt(feerate) => Btc(feerate.toLong).toLong
      }
    })
}

object ExtendedBitcoinClient {

  case class FundTransactionResponse(tx: Transaction, changepos: Int, fee: Double)

  case class SignTransactionResponse(tx: Transaction, complete: Boolean)

}

