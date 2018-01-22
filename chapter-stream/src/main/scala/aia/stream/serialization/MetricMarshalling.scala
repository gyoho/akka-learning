package aia.stream.serialization

import aia.stream.protocol.Metric
import spray.json._

trait MetricMarshalling extends EventMarshalling with DefaultJsonProtocol {
  implicit val metric = jsonFormat5(Metric)
}
