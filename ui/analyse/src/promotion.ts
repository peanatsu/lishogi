import { h } from 'snabbdom';
import * as ground from './ground';
import { bind, onInsert } from './util';
import * as util from 'shogiground/util';
import { Role } from 'shogiground/types';
import { canPiecePromote, promote as sPromote } from 'shogiops/util';
import AnalyseCtrl from './ctrl';
import { MaybeVNode, JustCaptured } from './interfaces';
import { parseChessSquare } from 'shogiops/compat';

interface Promoting {
  orig: Key;
  dest: Key;
  capture?: JustCaptured;
  role: Role;
  callback: Callback;
}

type Callback = (orig: Key, dest: Key, capture: JustCaptured | undefined, role: Boolean) => void;

let promoting: Promoting | undefined;

export function start(
  ctrl: AnalyseCtrl,
  orig: Key,
  dest: Key,
  capture: JustCaptured | undefined,
  callback: Callback
): boolean {
  const s = ctrl.shogiground.state;
  const piece = s.pieces.get(dest);
  if (!piece) return false;
  if (canPiecePromote(piece, parseChessSquare(orig)!, parseChessSquare(dest)!)) {
    promoting = {
      orig: orig,
      dest: dest,
      capture: capture,
      role: piece.role,
      callback,
    };
    ctrl.redraw();
    return true;
  }
  return false;
}

function finish(ctrl: AnalyseCtrl, role: Role): void {
  if (promoting) {
    ground.promote(ctrl.shogiground, promoting.dest, role);
    let prom: boolean = false;
    if (!['pawn', 'lance', 'knight', 'silver', 'bishop', 'rook'].includes(role)) prom = true;
    if (promoting.callback) promoting.callback(promoting.orig, promoting.dest, promoting.capture, prom);
  }
  promoting = undefined;
}

export function cancel(ctrl: AnalyseCtrl): void {
  if (promoting) {
    promoting = undefined;
    ctrl.shogiground.set(ctrl.cgConfig);
    ctrl.redraw();
  }
}

function renderPromotion(ctrl: AnalyseCtrl, dest: Key, pieces: string[], color: Color, orientation: Color): MaybeVNode {
  if (!promoting) return;

  let left = (8 - util.key2pos(dest)[0]) * (100/9);
  if (orientation === 'sente') left = util.key2pos(dest)[0] * (100/9);

  const vertical = color === orientation ? 'top' : 'bottom';

  return h(
    'div#promotion-choice.' + vertical,
    {
      hook: onInsert(el => {
        el.addEventListener('click', _ => cancel(ctrl));
        el.oncontextmenu = () => false;
      }),
    },
    pieces.map(function (serverRole: Role, i) {
      let top = (i + util.key2pos(dest)[1]) * (100/9);
      if (orientation === 'sente') top = (9 - (i + util.key2pos(dest)[1])) * (100/9);
      return h(
        'square',
        {
          attrs: {
            style: `top:${top}%;left:${left}%;`,
          },
          hook: bind('click', e => {
            e.stopPropagation();
            finish(ctrl, serverRole);
          }),
        },
        [h(`piece.${serverRole}.${color}`)]
      );
    })
  );
}

export function view(ctrl: AnalyseCtrl): MaybeVNode {
  if (!promoting) return;

  const roles: Role[] =
    ctrl.shogiground.state.orientation === 'gote'
      ? [sPromote(promoting.role), promoting.role]
      : [promoting.role, sPromote(promoting.role)];

  return renderPromotion(
    ctrl,
    promoting.dest,
    roles,
    promoting.dest[1] >= '7' ? 'sente' : 'gote',
    ctrl.shogiground.state.orientation
  );
}
