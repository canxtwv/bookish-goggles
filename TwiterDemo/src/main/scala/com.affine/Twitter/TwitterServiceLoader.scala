package com.affine.Twitter

import java.io.File
import akka.actor._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.softwaremill.macwire._
import com.typesafe.config.ConfigFactory
import com.affine.Twitter.entity._
import io.surfkit.typebus.bus.TypebusApplication
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import io.surfkit.typebus.bus.kafka.{TypebusKafkaConsumer, TypebusKafkaProducer}
import io.surfkit.typebus.event.ServiceIdentifier

class TwitterServiceLoader()
  extends Actor
    with CassandraPersistenceComponents
    with ActorLogging{

  implicit val system = context.system
  implicit val actorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

  // https://doc.akka.io/docs/akka-management/current/bootstrap/index.html
  // Akka Management hosts the HTTP routes used by bootstrap
  AkkaManagement(system).start()
  // Starting the bootstrap process needs to be done explicitly
  ClusterBootstrap(system).start()

  override def serviceLocator: ServiceLocator = ???

  // Members declared in com.lightbend.lagom.scaladsl.server.AkkaManagementComponents
  def coordinatedShutdown: akka.actor.CoordinatedShutdown = akka.actor.CoordinatedShutdown(context.system)

  // Members declared in com.lightbend.lagom.scaladsl.cluster.ClusterComponents
  def environment: play.api.Environment = play.api.Environment(new File("."), this.getClass.getClassLoader, play.api.Mode.Dev)

  // Members declared in com.lightbend.lagom.scaladsl.persistence.ReadSidePersistenceComponents
  def actorSystem: akka.actor.ActorSystem = context.system
  def configuration: play.api.Configuration = play.api.Configuration(ConfigFactory.load)
  def executionContext: scala.concurrent.ExecutionContext = context.system.dispatcher
  def materializer: akka.stream.Materializer = actorMaterializer


  // Members declared in com.lightbend.lagom.scaladsl.playjson.RequiresJsonSerializerRegistry
  def jsonSerializerRegistry: com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry = ???

  val TwitteruserEntity = new TwitteruserEntity
  persistentEntityRegistry.register(wire[TwitteruserEntity])


  lazy val serviceIdentity = ServiceIdentifier("twitter")

  // only want to activate and join cluster in certain cases
  //ZookeeperClusterSeed(system).join()
  lazy val producer = new TypebusKafkaProducer(serviceIdentity, system)
  lazy val service = new TwitterService(serviceIdentity, producer, system, new TwitteruserEntityDatabase(persistentEntityRegistry) )
  lazy val consumer = new TypebusKafkaConsumer(service, producer, system)

  TypebusApplication
  (
    system,
    serviceIdentity,
    producer,
    service,
    consumer
  )


  override def receive = {
    case _ =>
  }

}

object TwitterServiceLoader extends App{
  val system = ActorSystem("twitter")
  system.actorOf(Props(new TwitterServiceLoader))

  Thread.currentThread().join()
}

