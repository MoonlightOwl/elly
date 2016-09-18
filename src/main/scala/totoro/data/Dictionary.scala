package totoro.data

import scala.util.Random


object Dictionary {
  val random = new Random(System.currentTimeMillis())

  def get(words: List[String]): String =
    words(random.nextInt(words.length))


  def Hello = get(List("o/", "hi!", "hello!", "i'm glad to see you!", "hey!", "good morning!",
  ":3", "good to see ya!"))

  def Help = get(List("Google will help you! :P", "but what can i do?..", "help! help! someone, help him!",
    "your request will be processed within 2 working days."))

  def Kawaii = get(List("kawaii!", "nyaa!", "desu!", ":3", ":P", "unicorns freedom!", "nya-a-a...", "^_^"))

  def Thanks = get(List("thank you!", "thanks!", "thx!", "cheers!",
    "thanks a lot!", "i owe you one!", "arigatou!"))

  def Wtf = get(List("i'm sorry, what?", "wtf?", "do you speak english?", "no way!", "wut?!",
    "baka!", ".-.", "what language is this?"))

  def YesNo = get(List("yes!", "no!", "kawaii :3", "yeah!", "nope!", "maybe...",
    "yep.", "i'n mot sure yet...", "not really."))

  def Music = get(List("Infected Mushroom!", "GoldFish!", "The Sounds!", "The Strokes!", "Prodigy!", "Grimes!",
    "Ohrbooten!", "Charisma.com!", "Deep Forest!", "Uncle Outrage!", "Hadouken!", "The Glitch Mob!"))
}
