package akkaviz.serialization

import java.lang.reflect.Field

import scala.collection.mutable

trait ClassInspector {

  def underlyingClass: Class[_]

  def fields: Seq[ClassInspector.ClassField]

  def allFieldNames: Set[String]

  def isScalaObject: Boolean

  def inspect(obj: Any, fields: Set[String] = allFieldNames): Map[String, Any]

  def inspect(obj: Any, field: String): Option[Any] = inspect(obj, Set(field)).values.headOption

  override def toString: String = {
    val fieldsStr = fields.map(f => s"${f.name}:${f.signature}").mkString(",")
    s"ClassInspector(underlyingClass=${underlyingClass},fields=${fieldsStr},isObject=${isScalaObject})"
  }

}

object ClassInspector {

  case class Accessor(fieldName: String, field: Field)

  sealed trait FieldType

  case object Val extends FieldType

  case object Var extends FieldType

  case class ClassField(name: String, fullName: String, fieldType: FieldType, resultClass: Class[_], signature: String)

  private val rm = scala.reflect.runtime.currentMirror

  case object UnableToInspectField

  def of(clazz: Class[_]): ClassInspector = {
    val t = rm.classSymbol(clazz).toType
    val isM = t.typeSymbol.isModuleClass

    val reflectedFields = t.members.filter(_.isTerm).map(_.asTerm).filter(t => t.isVal || t.isVar).map {
      s =>
        val fn = s.fullName
        val name = fn.split('.').last
        ClassField(
          name,
          fn,
          if (s.isVal) Val else Var,
          rm.runtimeClass(s.typeSignature),
          s.typeSignature.toString
        )
    }
    new ClassInspector {
      val underlyingClass: Class[_] = clazz
      private[this] val f = reflectedFields.toSeq
      private[this] val accessors: List[Accessor] = f.flatMap {
        cf =>
          try {
            val field = underlyingClass.getDeclaredField(cf.name)
            field.setAccessible(true)
            Some(Accessor(cf.name, field))
          } catch {
            case t: Throwable => None
          }
      }.toList

      override val allFieldNames: Set[String] = f.map(_.name).toSet

      override def inspect(obj: Any, fieldNames: Set[String] = allFieldNames): Map[String, Any] = {
        val result = mutable.Map[String, Any]()
        accessors.foreach {
          case Accessor(fieldName, field) =>
            try {
              // It will fail if field is passed as default class constructor and is not referenced in class
              // There is also a bug(?) in scalac, which makes it unavailable if it was referenced from closure
              // That happens eg. in FSM trait, as there are only closures
              // Fields are usually available in the class, but their names are mangled so it is not obvious where
              // to find them reliably
              result += (fieldName -> field.get(obj))
            } catch {
              case t: Throwable =>
                result += (fieldName -> UnableToInspectField)
            }
        }
        result.toMap
      }

      override def isScalaObject: Boolean = isM

      override def fields: Seq[ClassField] = f
    }
  }
}
