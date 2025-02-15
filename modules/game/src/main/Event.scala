package lila.game

import play.api.libs.json._

import shogi.{
  Centis,
  PromotableRole,
  Pos,
  Color,
  Situation,
  Data,
  Move => ShogiMove,
  Drop => ShogiDrop,
  Clock => ShogiClock,
  Status
}
import JsonView._
import lila.chat.{ PlayerLine, UserLine }
import lila.common.ApiVersion

sealed trait Event {
  def typ: String
  def data: JsValue
  def only: Option[Color]   = None
  def owner: Boolean        = false
  def watcher: Boolean      = false
  def troll: Boolean        = false
  def moveBy: Option[Color] = None
}

object Event {

  sealed trait Empty extends Event {
    def data = JsNull
  }

  object Start extends Empty {
    def typ = "start"
  }

  object MoveOrDrop {

    def data(
        fen: String,
        check: Boolean,
        threefold: Boolean,
        state: State,
        clock: Option[ClockEvent],
        possibleMoves: Map[Pos, List[Pos]],
        possibleDrops: Option[List[Pos]],
        crazyData: Option[Data]
    )(extra: JsObject) = {
      extra ++ Json
        .obj(
          "fen"   -> fen,
          "ply"   -> state.turns,
          "dests" -> PossibleMoves.oldJson(possibleMoves)
        )
        .add("clock" -> clock.map(_.data))
        .add("status" -> state.status)
        .add("winner" -> state.winner)
        .add("check" -> check)
        .add("threefold" -> threefold)
        .add("sDraw" -> state.senteOffersDraw)
        .add("gDraw" -> state.goteOffersDraw)
        .add("crazyhouse" -> crazyData)
        .add("drops" -> possibleDrops.map { squares =>
          JsString(squares.map(_.key).mkString)
        })
    }
  }

  case class Move(
      orig: Pos,
      dest: Pos,
      san: String,
      fen: String,
      check: Boolean,
      threefold: Boolean,
      promotion: Boolean,
      state: State,
      clock: Option[ClockEvent],
      possibleMoves: Map[Pos, List[Pos]],
      possibleDrops: Option[List[Pos]],
      crazyData: Option[Data]
  ) extends Event {
    val promS = { if (promotion) "+" else "" }
    def typ = "move"
    def data = {
      MoveOrDrop.data(fen, check, threefold, state, clock, possibleMoves, possibleDrops, crazyData) {
        Json
          .obj(
            "uci" -> s"${orig.key}${dest.key}$promS",
            "san" -> san
          )
          .add("promotion" -> promotion)
      }
    }
    override def moveBy = Some(!state.color)
  }
  object Move {
    def apply(
        move: ShogiMove,
        situation: Situation,
        state: State,
        clock: Option[ClockEvent],
        crazyData: Option[Data]
    ): Move =
      Move(
        orig = move.orig,
        dest = move.dest,
        san = shogi.format.pgn.Dumper(move),
        fen = shogi.format.Forsyth.exportSituation(situation),
        check = situation.check,
        threefold = situation.threefoldRepetition,
        promotion = move.promotion,
        state = state,
        clock = clock,
        possibleMoves = situation.destinations,
        possibleDrops = situation.drops,
        crazyData = crazyData
      )
  }

  case class Drop(
      role: shogi.Role,
      pos: Pos,
      san: String,
      fen: String,
      check: Boolean,
      threefold: Boolean,
      state: State,
      clock: Option[ClockEvent],
      possibleMoves: Map[Pos, List[Pos]],
      crazyData: Option[Data],
      possibleDrops: Option[List[Pos]]
  ) extends Event {
    def typ = "drop"
    def data =
      MoveOrDrop.data(fen, check, threefold, state, clock, possibleMoves, possibleDrops, crazyData) {
        Json.obj(
          "role" -> role.name,
          "uci"  -> s"${role.pgn}*${pos.key}",
          "san"  -> san
        )
      }
    override def moveBy = Some(!state.color)
  }
  object Drop {
    def apply(
        drop: ShogiDrop,
        situation: Situation,
        state: State,
        clock: Option[ClockEvent],
        crazyData: Option[Data]
    ): Drop =
      Drop(
        role = drop.piece.role,
        pos = drop.pos,
        san = shogi.format.pgn.Dumper(drop),
        fen = shogi.format.Forsyth.exportSituation(situation),
        check = situation.check,
        threefold = situation.threefoldRepetition,
        state = state,
        clock = clock,
        possibleMoves = situation.destinations,
        possibleDrops = situation.drops,
        crazyData = crazyData
      )
  }

  object PossibleMoves {

    def json(moves: Map[Pos, List[Pos]], apiVersion: ApiVersion) =
      if (apiVersion gte 4) newJson(moves)
      else oldJson(moves)

    def newJson(moves: Map[Pos, List[Pos]]) =
      if (moves.isEmpty) JsNull
      else {
        val sb    = new java.lang.StringBuilder(128)
        var first = true
        moves foreach { case (orig, dests) =>
          if (first) first = false
          else sb append " "
          sb append orig.key
          dests foreach { sb append _.key }
        }
        JsString(sb.toString)
      }

    def oldJson(moves: Map[Pos, List[Pos]]) =
      if (moves.isEmpty) JsNull
      else
        moves.foldLeft(JsObject(Nil)) { case (res, (o, d)) =>
          res + (o.key -> JsString(d map (_.key) mkString))
        }
  }

  case class RedirectOwner(
      color: Color,
      id: String,
      cookie: Option[JsObject]
  ) extends Event {
    def typ = "redirect"
    def data =
      Json
        .obj(
          "id"  -> id,
          "url" -> s"/$id"
        )
        .add("cookie" -> cookie)
    override def only = Some(color)
  }

  case class PlayerMessage(line: PlayerLine) extends Event {
    def typ            = "message"
    def data           = lila.chat.JsonView(line)
    override def owner = true
    override def troll = false
  }

  case class UserMessage(line: UserLine, w: Boolean) extends Event {
    def typ              = "message"
    def data             = lila.chat.JsonView(line)
    override def troll   = line.troll
    override def watcher = w
    override def owner   = !w
  }

  // for mobile app BC only
  case class End(winner: Option[Color]) extends Event {
    def typ  = "end"
    def data = Json.toJson(winner)
  }

  case class EndData(game: Game, ratingDiff: Option[RatingDiffs]) extends Event {
    def typ = "endData"
    def data =
      Json
        .obj(
          "winner" -> game.winnerColor,
          "status" -> game.status
        )
        .add("clock" -> game.clock.map { c =>
          Json.obj(
            "sc" -> c.remainingTime(Color.Sente).centis,
            "gc" -> c.remainingTime(Color.Gote).centis,
            "sp" -> c.curPeriod(Color.Sente),
            "gp" -> c.curPeriod(Color.Gote)
          )
        })
        .add("ratingDiff" -> ratingDiff.map { rds =>
          Json.obj(
            Color.Sente.name -> rds.sente,
            Color.Gote.name  -> rds.gote
          )
        })
        .add("boosted" -> game.boosted)
  }

  case object Reload extends Empty {
    def typ = "reload"
  }
  case object ReloadOwner extends Empty {
    def typ            = "reload"
    override def owner = true
  }

  private def reloadOr[A: Writes](typ: String, data: A) = Json.obj("t" -> typ, "d" -> data)

  // use t:reload for mobile app BC,
  // but send extra data for the web to avoid reloading
  case class RematchOffer(by: Option[Color]) extends Event {
    def typ            = "reload"
    def data           = reloadOr("rematchOffer", by)
    override def owner = true
  }

  case class RematchTaken(nextId: Game.ID) extends Event {
    def typ  = "reload"
    def data = reloadOr("rematchTaken", nextId)
  }

  case class DrawOffer(by: Option[Color]) extends Event {
    def typ            = "reload"
    def data           = reloadOr("drawOffer", by)
    override def owner = true
  }

  case class ClockInc(color: Color, time: Centis) extends Event {
    def typ = "clockInc"
    def data =
      Json.obj(
        "color" -> color,
        "time"  -> time.centis
      )
  }

  sealed trait ClockEvent extends Event

  case class Clock(
      sente: Centis,
      gote: Centis,
      sPer: Int = 0,
      gPer: Int = 0,
      nextLagComp: Option[Centis] = None
  ) extends ClockEvent {
    def typ = "clock"
    def data =
      Json
        .obj(
          "sente" -> sente.toSeconds,
          "gote"  -> gote.toSeconds,
          "sPer"  -> sPer,
          "gPer"  -> gPer
        )
        .add("lag" -> nextLagComp.collect { case Centis(c) if c > 1 => c })
  }
  object Clock {
    def apply(clock: ShogiClock): Clock =
      Clock(
        sente = clock remainingTime Color.Sente,
        gote = clock remainingTime Color.Gote,
        sPer = clock curPeriod Color.Sente,
        gPer = clock curPeriod Color.Gote,
        nextLagComp = clock lagCompEstimate clock.color
      )
  }

  case class Berserk(color: Color) extends Event {
    def typ  = "berserk"
    def data = Json.toJson(color)
  }

  case class CorrespondenceClock(sente: Float, gote: Float) extends ClockEvent {
    def typ  = "cclock"
    def data = Json.obj("sente" -> sente, "gote" -> gote)
  }
  object CorrespondenceClock {
    def apply(clock: lila.game.CorrespondenceClock): CorrespondenceClock =
      CorrespondenceClock(clock.senteTime, clock.goteTime)
  }

  case class CheckCount(sente: Int, gote: Int) extends Event {
    def typ = "checkCount"
    def data =
      Json.obj(
        "sente" -> sente,
        "gote"  -> gote
      )
  }

  case class State(
      color: Color,
      turns: Int,
      status: Option[Status],
      winner: Option[Color],
      senteOffersDraw: Boolean,
      goteOffersDraw: Boolean
  ) extends Event {
    def typ = "state"
    def data =
      Json
        .obj(
          "color" -> color,
          "turns" -> turns
        )
        .add("status" -> status)
        .add("winner" -> winner)
        .add("sDraw" -> senteOffersDraw)
        .add("gDraw" -> goteOffersDraw)
  }

  case class TakebackOffers(
      sente: Boolean,
      gote: Boolean
  ) extends Event {
    def typ = "takebackOffers"
    def data =
      Json
        .obj()
        .add("sente" -> sente)
        .add("gote" -> gote)
    override def owner = true
  }

  case class Crowd(
      sente: Boolean,
      gote: Boolean,
      watchers: Option[JsValue]
  ) extends Event {
    def typ = "crowd"
    def data =
      Json
        .obj(
          "sente" -> sente,
          "gote"  -> gote
        )
        .add("watchers" -> watchers)
  }
}
