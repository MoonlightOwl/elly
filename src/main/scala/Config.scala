import java.io.File

import com.typesafe.config.ConfigFactory

/**
  * Constants and parameters storage
  */
object Config {
  val config = ConfigFactory.parseFile(new File("./config/elly.conf"))

  val Server = config.getString("server.url")
  val Port = config.getInt("server.port")
  val Channel = config.getString("server.channel")

  val Nickname = config.getString("user.nickname")
  val Domain = config.getString("user.domain")
  val RealName = config.getString("user.realname")
  val Password = config.getString("user.password")
}
