package shogi

import format.Uci

case class Situation(board: Board, color: Color) {

  lazy val actors = board actorsOf color

  lazy val moves: Map[Pos, List[Move]] = board.variant.validMoves(this)

  lazy val playerCanCapture: Boolean = moves exists (_._2 exists (_.captures))

  lazy val destinations: Map[Pos, List[Pos]] = moves.view.mapValues { _ map (_.dest) }.to(Map)

  def drops: Option[List[Pos]] =
    board.variant match {
      case v: variant.Standard.type => v possibleDrops this
      case _                        => None
    }

  lazy val kingPos: Option[Pos] = board kingPosOf color

  lazy val check: Boolean = board check color

  def checkSquare = if (check) kingPos else None

  def history = board.history

  def checkMate: Boolean = board.variant checkmate this

  def staleMate: Boolean = board.variant staleMate this

  def autoDraw: Boolean = board.autoDraw // || board.variant.specialDraw(this)

  def opponentHasInsufficientMaterial: Boolean = board.variant.opponentHasInsufficientMaterial(this)

  lazy val threefoldRepetition: Boolean = false

  def variantEnd = board.variant specialEnd this

  def tryRule = board.tryRule

  def perpetualCheck = board.perpetualCheck

  def end: Boolean = checkMate || staleMate || autoDraw || variantEnd || tryRule || perpetualCheck

  def winner: Option[Color] = board.variant.winner(this)

  def playable(strict: Boolean): Boolean =
    (board valid strict) && !end && !copy(color = !color).check

  lazy val status: Option[Status] =
    if (checkMate) Status.Mate.some
    else if (variantEnd) Status.VariantEnd.some
    else if (staleMate) Status.Stalemate.some
    else if (tryRule) Status.Impasse.some
    else if (perpetualCheck) Status.PerpetualCheck.some
    else if (autoDraw) Status.Draw.some
    else none

  def move(from: Pos, to: Pos, promotion: Boolean): Valid[Move] = {
    board.variant.move(this, from, to, promotion)
  }

  def move(uci: Uci.Move): Valid[Move] = {
    board.variant.move(this, uci.orig, uci.dest, uci.promotion)
  }

  def drop(role: Role, pos: Pos): Valid[Drop] =
    board.variant.drop(this, role, pos)

  def withHistory(history: History) =
    copy(
      board = board withHistory history
    )

  def withVariant(variant: shogi.variant.Variant) =
    copy(
      board = board withVariant variant
    )

  def unary_! = copy(color = !color)
}

object Situation {

  def apply(variant: shogi.variant.Variant): Situation = Situation(Board init variant, Sente)
}
