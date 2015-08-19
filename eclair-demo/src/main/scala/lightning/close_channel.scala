// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!

package lightning


import com.trueaccord.scalapb.Descriptors

@SerialVersionUID(0L)
final case class close_channel(
    sig: lightning.signature,
    closeFee: Long
    ) extends com.trueaccord.scalapb.GeneratedMessage with com.trueaccord.scalapb.Message[close_channel] with com.trueaccord.lenses.Updatable[close_channel] {
    @transient
    lazy val serializedSize: Int = {
      var __size = 0
      __size += 1 + com.google.protobuf.CodedOutputStream.computeRawVarint32Size(sig.serializedSize) + sig.serializedSize
      __size += com.google.protobuf.CodedOutputStream.computeUInt64Size(2, closeFee)
      __size
    }
    def writeTo(output: com.google.protobuf.CodedOutputStream): Unit = {
      output.writeTag(1, 2)
      output.writeRawVarint32(sig.serializedSize)
      sig.writeTo(output)
      output.writeUInt64(2, closeFee)
    }
    def mergeFrom(__input: com.google.protobuf.CodedInputStream): lightning.close_channel = {
      var __sig = this.sig
      var __closeFee = this.closeFee
      var _done__ = false
      while (!_done__) {
        val _tag__ = __input.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __sig = com.trueaccord.scalapb.LiteParser.readMessage(__input, __sig)
          case 16 =>
            __closeFee = __input.readUInt64()
          case tag => __input.skipField(tag)
        }
      }
      lightning.close_channel(
          sig = __sig,
          closeFee = __closeFee
      )
    }
    def withSig(__v: lightning.signature): close_channel = copy(sig = __v)
    def withCloseFee(__v: Long): close_channel = copy(closeFee = __v)
    def getField(__field: Descriptors.FieldDescriptor): Any = {
      __field.number match {
        case 1 => sig
        case 2 => closeFee
      }
    }
    def companion = lightning.close_channel
}

object close_channel extends com.trueaccord.scalapb.GeneratedMessageCompanion[close_channel]  {
  implicit def messageCompanion: com.trueaccord.scalapb.GeneratedMessageCompanion[close_channel]  = this
  def fromFieldsMap(fieldsMap: Map[Int, Any]): lightning.close_channel = lightning.close_channel(
    sig = fieldsMap(1).asInstanceOf[lightning.signature],
    closeFee = fieldsMap(2).asInstanceOf[Long]
  )
  lazy val descriptor = new Descriptors.MessageDescriptor("close_channel", this,
    None, m = Seq(),
    e = Seq(),
    f = lightning.InternalFields_srcMainProtobufLightningProto.internalFieldsFor("lightning.close_channel"))
  lazy val defaultInstance = lightning.close_channel(
    sig = lightning.signature.defaultInstance,
    closeFee = 0L
  )
  implicit class close_channelLens[UpperPB](_l: com.trueaccord.lenses.Lens[UpperPB, close_channel]) extends com.trueaccord.lenses.ObjectLens[UpperPB, close_channel](_l) {
    def sig: com.trueaccord.lenses.Lens[UpperPB, lightning.signature] = field(_.sig)((c_, f_) => c_.copy(sig = f_))
    def closeFee: com.trueaccord.lenses.Lens[UpperPB, Long] = field(_.closeFee)((c_, f_) => c_.copy(closeFee = f_))
  }
  final val SIG_FIELD_NUMBER = 1
  final val CLOSE_FEE_FIELD_NUMBER = 2
}