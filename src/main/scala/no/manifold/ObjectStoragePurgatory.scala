package no.manifold

import argonaut._, Argonaut._
import scalaz._, Scalaz._
import dispatch._, Defaults._
import scala.concurrent.duration._
import scala.languageFeature.postfixOps
import scala.concurrent.Await
import org.constretto._
import Constretto._
/**
  * Created by jarnyste on 25/01/16.
  */
case class Cluster(datacenters: Option[Map[String, String]])
case class StorageCredentials(datacenterUrl: Option[String], authtok: String, storageToken: String, storageUrl: String, expiry: String)

object ObjectStoragePurgatory extends App {

  // deletes every file (presumably) in container
  // obviously: use with care

  val constretto = Constretto(List(properties("classpath:objectstore.properties")),"local")
  val publicAuth = constretto[String]("authUrl")
  val userName = constretto[String]("userName")
  val apiKey = constretto[String]("apiKey")
  val dataCenter = constretto[String]("dataCenter")
  val container = constretto[String]("container")


  implicit def clusterDecode = DecodeJson(c =>
    for {
      clusters <- (c --\ "clusters").as[Option[Map[String, String]]]
    } yield Cluster(datacenters = clusters)
  )

  def getFileList(creds: StorageCredentials, containerName: String): Future[List[String]] = {
    val u = creds.storageUrl
    println(s"accessing $u/$containerName")
    val t: Req = (url(u) / containerName) <:< Map("X-Auth-Token" -> creds.authtok)
    Http(t OK as.String).map(_.split("\n").toList)
  }

  val UserCredentials: Map[String, String] = Map("X-Auth-User" -> userName, "X-Auth-Key" -> apiKey)
  def deleteFileList(files: List[String],
                     storageCredentials: StorageCredentials,
                     container: String) = {
    files.map { file =>
      val deleteUrl = (url(storageCredentials.storageUrl) / container / file) <:< Map("X-Auth-Token" -> storageCredentials.authtok)
      Http(deleteUrl.DELETE OK as.String)
    }.sequence
  }
  def getCredentials(userName: String, apiKey: String, publicAuth: String): Future[StorageCredentials] = {
    val call = url(publicAuth) <:< UserCredentials

    val result = Http(call)
    for {
      header: Res <- result
    } yield {
      val authtok = header.getHeader("X-Auth-Token")
      val expiry = header.getHeader("X-Auth-Token-Expires")
      val storageToken = header.getHeader("X-Storage-Token")
      val storageUrl = header.getHeader("X-Storage-Url")
      val clusterData: Option[Cluster] = Parse.decodeOption[Cluster](header.getResponseBody)
      val datacenterUrl = for {
        cd <- clusterData
        c <- cd.datacenters
      } yield c(dataCenter)
      StorageCredentials(datacenterUrl, authtok, storageToken, storageUrl, expiry)
    }
  }
  val t: Future[StorageCredentials] = getCredentials(userName, apiKey, publicAuth)
  val r: StorageCredentials = Await.result(t, 10.seconds)

  val plist = for {
    credentials <- t
    purged <- getFileList(credentials, container)
    p <- deleteFileList(purged, credentials, container)
  } yield p

  println(Await.result(plist, 10 seconds))


}

