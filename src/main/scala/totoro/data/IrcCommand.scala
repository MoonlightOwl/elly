package totoro.data

import akka.util.ByteString

import scala.util._


case class IrcCommand(command: String, args: Seq[String], source: Option[String] = None)

object IrcCommand {
  def read(raw: ByteString): Try[IrcCommand] = {
    val regex = """(:([^ ]+) )?([A-Z0-9]+)( (.*))?""".r
    raw.utf8String match {
      case regex(_, sourceOrNull, command, _, argsStringOrNull) =>
        val source = Option(sourceOrNull)
        val argsRaw = Option(argsStringOrNull).map(_.split(" ", -1).toSeq).getOrElse(Nil)
        val args = argsRaw.indexWhere(_.startsWith(":")) match {
          case -1 => argsRaw
          case c => argsRaw.take(c) ++ Seq(argsRaw.drop(c).mkString(" ").drop(1))
        }
        Success(IrcCommand(command, args, source))
      case _ =>
        Failure(new Exception(s"Cannot parse '${raw.utf8String}'"))
    }
  }

  def write(cmd: IrcCommand): ByteString = {
    val p1 = cmd.source.map(":" + _ + " ").getOrElse("")
    val p2 = cmd.command
    val p3 = cmd.args match {
      case Nil => ""
      case last :: Nil => " :" + last
      case args => args.init.map(" " + _).mkString + " :" + args.last
    }
    ByteString(p1 + p2 + p3)
  }

  def Nick(nickname: String) = IrcCommand(s"NICK $nickname", Seq())
  def User(domain: String, realname: String) = IrcCommand(s"USER $domain 0 *", Seq(realname))
  def Identify(password: String) = IrcCommand("PRIVMSG NickServ", Seq(s"IDENTIFY $password"))
  def Join(channel: String) = IrcCommand(s"JOIN $channel", Seq())
  def PrivMsg(to: String, message: String) = IrcCommand(s"PRIVMSG $to", Seq(message))
  def Null() = IrcCommand("", Seq())
}