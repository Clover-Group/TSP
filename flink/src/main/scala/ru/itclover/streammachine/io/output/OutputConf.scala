package ru.itclover.streammachine.io.output


trait OutputConf


case class JDBCOutputConf(jdbcUrl: String,
                          sinkSchema: PGSegmentsSink,
                          driverName: String,
                          userName: Option[String] = None,
                          password: Option[String] = None,
                          batchInterval: Option[Int] = None
                           ) extends OutputConf