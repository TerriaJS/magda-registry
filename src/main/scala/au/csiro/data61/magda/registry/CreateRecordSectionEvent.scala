package au.csiro.data61.magda.registry

case class CreateRecordSectionEvent(section: RecordSection)

object CreateRecordSectionEvent {
  val ID = 2 // from EventTypes table
}