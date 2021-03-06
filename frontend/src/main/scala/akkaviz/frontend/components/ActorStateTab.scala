package akkaviz.frontend.components

import akkaviz.frontend.ActorPath
import akkaviz.frontend.ActorRepository.ActorState
import akkaviz.frontend.{Persistence, FancyColors}
import akkaviz.frontend.vis._
import akkaviz.protocol
import akkaviz.protocol.{ActorFailure, ThroughputMeasurement}
import org.scalajs.dom.html.{Input, UList}
import org.scalajs.dom.raw.HTMLInputElement
import org.scalajs.dom.{Element => domElement, _}
import rx.{Ctx, Rx, Var}

import scala.scalajs.js
import scala.scalajs.js.{|, Date}
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._

class ActorStateTab(
    actorState: Var[ActorState],
    upstreamSend: protocol.ApiClientMessage => Unit,
    actorMessagesOpener: (ActorPath) => Unit,
    failures: Rx[Seq[ActorFailure]]
)(implicit co: Ctx.Owner) extends ClosableTab {

  import ActorStateTab._
  import akkaviz.frontend.PrettyJson._

  import scalatags.rx.all._

  val name = actorState.now.path
  val tabId = stateTabId(actorState.now.path)

  private[this] def renderState(state: Var[ActorState]) = {

    lazy val fsmDiv = div(cls := s"fsm-graph", height := 250.px, clear.both).render
    def disableMaybe(isDead: Boolean): Modifier = if (isDead) disabled := "disabled" else ()

    val rendered = div(
      cls := "panel-body",
      div(state.map(_.isDead).map { isDead =>
        div(
          refreshButton(state.now.path)(disableMaybe(isDead)),
          killButton(state.now.path)(disableMaybe(isDead)),
          poisonPillButton(state.now.path)(disableMaybe(isDead)),
          actorMessagesButton(state.now.path),
          clear.both
        ).render
      }),
      fsmDiv,
      div(strong("Class: "), Rx(state().className.getOrElse[String]("Unknown class"))),
      div(strong("Is dead: "), Rx(state().isDead.toString)),
      div(strong("Internal state: "), pre(Rx(state().internalState.map(prettyPrintJson).getOrElse[String]("Internal state unknown")))),
      div(strong("Is FSM: "), Rx(state().fsmState.isDefined.toString)),
      div(Rx(state().fsmState.map { fs =>
        div(
          div(strong("FSM data:"), pre(prettyPrintJson(fs.currentData))),
          div(strong("FSM state:"), pre(prettyPrintJson(fs.currentState)))
        ).render
      }.getOrElse(div().render))),
      div(strong("Mailbox size: "), Rx(state().mailboxSize.map(_.toString).getOrElse[String]("Unknown"))),
      div(strong("Last updated: "), Rx(state().lastUpdatedAt.toISOString())),
      div(strong("Failures: "), failures.map(failureTable))
    ).render

    tabBody.appendChild(rendered)
    val fsmGraph = new FsmGraph(fsmDiv)
    state.map(_.fsmTransitions).foreach(fsmGraph.displayFsm)
  }

  private[this] def failureTable(failures: Seq[ActorFailure]) = {
    if (failures.isEmpty) em("No failures detected.").render
    else
      table(
        id := "failures-table",
        `class` := "table",
        thead(
          tr(th("Exception", cls := "col-md-6"), th("Supervisor decision", cls := "col-md-1"), th("Time", cls := "col-md-5"))
        ),
        tbody(
          for (f <- failures)
            yield tr(td(f.cause), td(f.decision), td(f.ts))
        )
      ).render
  }

  private[this] def refreshButton(actorRef: ActorPath) =
    a(cls := "btn btn-default", href := "#", role := "button", float.right,
      span(
        `class` := "imgbtn glyphicon glyphicon-refresh", " "
      ),
      onclick := { () =>
        upstreamSend(protocol.RefreshInternalState(actorRef))
      },
      "Refresh state")

  private[this] def killButton(actorRef: ActorPath) =
    a(cls := "btn btn-default", href := "#", role := "button", float.right,
      span(`class` := "glyphicons glyphicons-remove-sign"),
      onclick := { () =>
        upstreamSend(protocol.KillActor(actorRef))
      },
      "Kill")

  private[this] def poisonPillButton(actorRef: ActorPath) =
    a(cls := "btn btn-default", href := "#", role := "button", float.right,
      span(`class` := "glyphicons glyphicons-lab"),
      onclick := { () =>
        upstreamSend(protocol.PoisonPillActor(actorRef))
      },
      "PoisonPill")

  private[this] def actorMessagesButton(actorRef: ActorPath) =
    a(cls := "btn btn-default", href := "#", role := "button", float.right,
      onclick := { () =>
        actorMessagesOpener(actorRef)
      },
      "All messages")

  override def onCreate(): Unit = {
    renderState(actorState)
  }
}

object ActorStateTab {
  def stateTabId(path: ActorPath): String = {
    s"actor-state-${path.replaceAll("[\\/|\\.|\\\\|\\$]", "-").filterNot(_ == ':')}"
  }
}