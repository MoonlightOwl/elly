package totoro.data

import akka.util.ByteString

import scala.util._


case class Package(content: Seq[IrcCommand])

object Package {
  val Delimiter = ByteString("\r\n")
  val MaxFrameLength = 65536

  // TODO: add splitting by "\r\n" support
  def read(bytestring: ByteString): Try[Package] = {
    IrcCommand.read(bytestring) match {
      case Success(c) => Success(Package(Seq(c)))
      case Failure(e) => Failure(e)
    }
  }

  def write(p: Package): ByteString = {
    p.content.map(IrcCommand.write).foldLeft(ByteString())(_ ++ Delimiter ++ _)
  }
}
