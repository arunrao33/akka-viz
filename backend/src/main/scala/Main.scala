import akka.actor.{Actor, ActorSystem, Props}
import fsm.DiningHakkersOnFsm
import postoffice.PostOffice
import spray.SprayDemo

import scala.util.Random

object Main extends App {
  DiningHakkersOnFsm.run(ActorSystem("fsm"))
  PostOffice.run(ActorSystem("post-office"))
  SprayDemo.run(ActorSystem("spray"))

  val system = ActorSystem("small-demos")
  val lazyActorProps = Props(new Actor {
    override def receive: Receive = {
      case msg =>
        Thread.sleep(Random.nextInt(2000))
        sender() ! msg
    }
  })

  val lazyActor1 = system.actorOf(lazyActorProps, "lazy1")
  val lazyActor2 = system.actorOf(lazyActorProps, "lazy2")
  for (i <- 0 to 1000) {
    lazyActor1.tell("doit", lazyActor2)
  }
}
