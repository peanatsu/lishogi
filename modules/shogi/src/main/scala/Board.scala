package shogi

import Pos.posAt
import scalaz.Validation.FlatMap._
import scalaz.Validation.{ failureNel, success }
import variant.{ Standard, Variant }

case class Board(
    pieces: PieceMap,
    history: History,
    variant: Variant,
    crazyData: Option[Data] = None
) {

  import implicitFailures._

  def apply(at: Pos): Option[Piece] = pieces get at

  def apply(x: Int, y: Int): Option[Piece] = posAt(x, y) flatMap pieces.get

  lazy val actors: Map[Pos, Actor] = pieces map { case (pos, piece) =>
    (pos, Actor(piece, pos, this))
  }

  lazy val actorsOf: Color.Map[Seq[Actor]] = {
    val (s, g) = actors.values.toSeq.partition { _.color.sente }
    Color.Map(s, g)
  }

  def rolesOf(c: Color): List[Role] =
    pieces.values
      .collect {
        case piece if piece.color == c => piece.role
      }
      .to(List)

  def occupiedPawnFiles(c: Color): List[Int] =
    pieces
      .collect {
        case (pos, piece) if piece.color == c && piece.role == Pawn => pos.x
      }
      .to(List)

  def actorAt(at: Pos): Option[Actor] = actors get at

  def piecesOf(c: Color): Map[Pos, Piece] = pieces filter (_._2 is c)

  lazy val kingPos: Map[Color, Pos] = pieces.collect { case (pos, Piece(color, King)) =>
    color -> pos
  }

  def kingPosOf(c: Color): Option[Pos] = kingPos get c

  def check(c: Color): Boolean = c.sente.fold(checkSente, checkGote)

  lazy val checkSente = checkOf(Sente)
  lazy val checkGote  = checkOf(Gote)

  private def checkOf(c: Color): Boolean =
    kingPosOf(c) exists { kingPos =>
      variant.kingThreatened(this, !c, kingPos)
    }

  def destsFrom(from: Pos): Option[List[Pos]] = actorAt(from) map (_.destinations)

  def seq(actions: Board => Valid[Board]*): Valid[Board] =
    actions.foldLeft(success(this): Valid[Board])(_ flatMap _)

  def place(piece: Piece) =
    new {
      def at(at: Pos): Valid[Board] =
        if (pieces contains at) failureNel("Cannot place at occupied " + at)
        else success(copy(pieces = pieces + ((at, piece))))
    }

  def place(piece: Piece, at: Pos): Option[Board] =
    if (pieces contains at) None
    else Some(copy(pieces = pieces + ((at, piece))))

  def take(at: Pos): Option[Board] =
    if (pieces contains at) Some(copy(pieces = pieces - at))
    else None

  def move(orig: Pos, dest: Pos): Option[Board] =
    if (pieces contains dest) None
    else
      pieces get orig map { piece =>
        copy(pieces = pieces - orig + ((dest, piece)))
      }

  def taking(orig: Pos, dest: Pos, taking: Option[Pos] = None): Option[Board] =
    for {
      piece <- pieces get orig
      takenPos = taking getOrElse dest
      if pieces contains takenPos
    } yield copy(pieces = pieces - takenPos - orig + (dest -> piece))

  def move(orig: Pos) =
    new {
      def to(dest: Pos): Valid[Board] = {
        if (pieces contains dest) failureNel("Cannot move to occupied " + dest)
        else
          pieces get orig map { piece =>
            copy(pieces = pieces - orig + (dest -> piece))
          } toSuccess ("No piece at " + orig + " to move")
      }
    }

  lazy val occupation: Color.Map[Set[Pos]] = Color.Map { color =>
    pieces.collect { case (pos, piece) if piece is color => pos }.to(Set)
  }

  def hasPiece(p: Piece) = pieces.values exists (p ==)

  def promote(pos: Pos, promotedRole: PromotableRole): Option[Board] =
    for {
      piece <- apply(pos)
      b2    <- take(pos)
      b3    <- b2.place(Piece(piece.color, promotedRole), pos) //todo
    } yield b3

  def withHistory(h: History): Board = copy(history = h)

  def withPieces(newPieces: PieceMap) = copy(pieces = newPieces)

  def withVariant(v: Variant): Board = {
    copy(variant = v).ensureCrazyData
  }

  def withCrazyData(data: Data)         = copy(crazyData = Some(data))
  def withCrazyData(data: Option[Data]) = copy(crazyData = data)
  def withCrazyData(f: Data => Data): Board =
    withCrazyData(f(crazyData | Data.init))

  def ensureCrazyData = withCrazyData(crazyData | Data.init)

  def updateHistory(f: History => History) = copy(history = f(history))

  def count(p: Piece): Int = pieces.values count (_ == p)
  def count(c: Color): Int = pieces.values count (_.color == c)

  def kingsEntered: Boolean = {
    ((kingPosOf(Gote) exists (pos => Gote.promotableZone contains pos.y)) &&
    (kingPosOf(Sente) exists (pos => Sente.promotableZone contains pos.y)))
  }

  def tryRule: Boolean =
    (kingPosOf(Sente) == posAt(5, 9)) || (kingPosOf(Gote) == posAt(5, 1))

  def tryRuleColor: Option[Color] =
    if (kingPosOf(Sente) == posAt(5, 9)) Sente.some
    else if (kingPosOf(Gote) == posAt(5, 1)) Gote.some
    else none

  def perpetualCheckColor: Option[Color] = {
    val checks = history.checkCount
    if (checks.sente >= 4 && checks.gote >= 4)
      return None
    else if (checks.sente >= 4)
      return Some(Sente)
    else if (checks.gote >= 4)
      return Some(Gote)
    else
      return None
  }

  def perpetualCheck: Boolean = {
    val checks = history.checkCount
    history.fivefoldRepetition && (checks.sente >= 4 || checks.gote >= 4)
  }

  def autoDraw: Boolean = {
    history.fivefoldRepetition && !perpetualCheck
  }

  def situationOf(color: Color) = Situation(this, color)

  def visual = format.Visual >> this

  def valid(strict: Boolean) = variant.valid(this, strict)

  def materialImbalance: Int = variant.materialImbalance(this)

  override def toString = s"$variant Position after ${history.lastMove}\n$visual"
}

object Board {

  def apply(pieces: Iterable[(Pos, Piece)], variant: Variant): Board =
    Board(pieces.toMap, History(), variant, variantCrazyData(variant))

  def init(variant: Variant): Board = Board(variant.pieces, variant)

  def empty(variant: Variant): Board = Board(Nil, variant)

  private def variantCrazyData(variant: Variant) =
    Some(Data.init)
}
