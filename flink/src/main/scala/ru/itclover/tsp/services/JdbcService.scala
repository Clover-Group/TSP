package ru.itclover.tsp.services

import java.sql.DriverManager

import scala.util.Try

object JdbcService {

  def fetchFieldsTypesInfo(driverName: String, jdbcUrl: String, query: String): Try[Seq[(Symbol, Class[_])]] = {
    val classTry: Try[Class[_]] = Try(Class.forName(driverName))

    val connectionTry = Try(DriverManager.getConnection(jdbcUrl))
    for {
      _          <- classTry
      connection <- connectionTry
      resultSet  <- Try(connection.createStatement().executeQuery(s"SELECT * FROM (${query}) as mainQ LIMIT 1"))
      metaData   <- Try(resultSet.getMetaData)
    } yield {
      (1 to metaData.getColumnCount).map { i: Int =>
        val className = metaData.getColumnClassName(i)
        (Symbol(metaData.getColumnName(i)), Class.forName(className))
      }
    }
  }

  def fetchAvailableKeys(driverName: String, jdbcUrl: String, query: String, keyColumn: Symbol): Try[Set[Symbol]] = {
    val classTry: Try[Class[_]] = Try(Class.forName(driverName))

    val connectionTry = Try(DriverManager.getConnection(jdbcUrl))
    for {
      _          <- classTry
      connection <- connectionTry
      resultSet  <- Try(connection.createStatement().executeQuery(s"SELECT DISTINCT(${keyColumn.name}) FROM (${query})"))
    } yield {
      Iterator.continually(Unit).takeWhile(_ => resultSet.next()).map(_ => Symbol(resultSet.getString(1))).toSet
    }
  }
}
