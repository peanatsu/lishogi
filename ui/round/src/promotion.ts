import { h } from 'snabbdom';
import * as ground from './ground';
import * as cg from 'shogiground/types';
import { DrawShape } from 'shogiground/draw';
import * as xhr from './xhr';
import { key2pos } from 'shogiground/util';
import { bind } from './util';
import RoundController from './ctrl';
import { onInsert } from './util';
import { MaybeVNode } from './interfaces';
import { promote as sPromote } from 'shogiops/util';

interface Promoting {
  move: [cg.Key, cg.Key];
  pre: boolean;
  meta: cg.MoveMetadata;
  role: cg.Role;
}

let promoting: Promoting | undefined;
let prePromotionRole: cg.Role | undefined;

export function sendPromotion(
  ctrl: RoundController,
  orig: cg.Key,
  dest: cg.Key,
  role: cg.Role,
  meta: cg.MoveMetadata
): boolean {
  let promotion: boolean = false;
  if (!['pawn', 'lance', 'knight', 'silver', 'bishop', 'rook'].includes(role)) {
    ground.promote(ctrl.shogiground, dest);
    promotion = true;
  }
  ctrl.sendMove(orig, dest, promotion, meta);
  return true;
}

export function start(
  ctrl: RoundController,
  orig: cg.Key,
  dest: cg.Key,
  meta: cg.MoveMetadata = {} as cg.MoveMetadata
): boolean {
  const d = ctrl.data,
    piece = ctrl.shogiground.state.pieces.get(dest),
    premovePiece = ctrl.shogiground.state.pieces.get(orig);
  if (
    ((piece && ['pawn', 'lance', 'knight', 'silver', 'bishop', 'rook'].includes(piece.role) && !premovePiece) ||
      (premovePiece && ['pawn', 'lance', 'knight', 'silver', 'bishop', 'rook'].includes(premovePiece.role))) &&
    (((['7', '8', '9'].includes(dest[1]) || ['7', '8', '9'].includes(orig[1])) && d.player.color === 'sente') ||
      ((['1', '2', '3'].includes(dest[1]) || ['1', '2', '3'].includes(orig[1])) && d.player.color === 'gote'))
  ) {
    if (
      piece &&
      ((['pawn', 'lance'].includes(piece.role) &&
        ((dest[1] === '9' && piece.color === 'sente') || (dest[1] === '1' && piece.color === 'gote'))) ||
        (piece.role === 'knight' &&
          ((['8', '9'].includes(dest[1]) && piece.color === 'sente') ||
            (['1', '2'].includes(dest[1]) && piece.color === 'gote'))))
    ) {
      return sendPromotion(ctrl, orig, dest, sPromote(piece.role), meta);
    }
    if (prePromotionRole && meta && meta.premove) return sendPromotion(ctrl, orig, dest, prePromotionRole, meta);
    if (!meta.ctrlKey && !promoting && ctrl.keyboardMove && ctrl.keyboardMove.justSelected()) {
      if (premovePiece) setPrePromotion(ctrl, dest, sPromote(premovePiece.role));
      return true;
    }
    const promotionRole = premovePiece ? premovePiece.role : piece ? piece.role : 'pawn';
    promoting = {
      move: [orig, dest],
      pre: !!premovePiece,
      meta,
      role: promotionRole,
    };
    ctrl.redraw();
    return true;
  }
  return false;
}

function setPrePromotion(ctrl: RoundController, dest: cg.Key, role: cg.Role): void {
  prePromotionRole = role;
  ctrl.shogiground.setAutoShapes([
    {
      orig: dest,
      piece: {
        color: ctrl.data.player.color,
        role,
        opacity: 0.8,
      },
      brush: '',
    } as DrawShape,
  ]);
}

export function cancelPrePromotion(ctrl: RoundController) {
  if (prePromotionRole) {
    ctrl.shogiground.setAutoShapes([]);
    prePromotionRole = undefined;
    ctrl.redraw();
  }
}

function finish(ctrl: RoundController, role: cg.Role) {
  if (promoting) {
    const info = promoting;
    promoting = undefined;
    if (info.pre) setPrePromotion(ctrl, info.move[1], role);
    else sendPromotion(ctrl, info.move[0], info.move[1], role, info.meta);
    ctrl.redraw();
  }
}

export function cancel(ctrl: RoundController) {
  cancelPrePromotion(ctrl);
  ctrl.shogiground.cancelPremove();
  if (promoting) xhr.reload(ctrl).then(ctrl.reload);
  promoting = undefined;
}

function renderPromotion(
  ctrl: RoundController,
  dest: cg.Key,
  roles: cg.Role[],
  color: Color,
  orientation: Color
): MaybeVNode {
  var left = (8 - key2pos(dest)[0]) * (100/9);
  if (orientation === 'sente') left = key2pos(dest)[0] * (100/9);
  var vertical = color === orientation ? 'top' : 'bottom';

  return h(
    'div#promotion-choice.' + vertical,
    {
      hook: onInsert(el => {
        el.addEventListener('click', () => cancel(ctrl));
        el.addEventListener('contextmenu', e => {
          e.preventDefault();
          return false;
        });
      }),
    },
    roles.map((serverRole, i) => {
      var top = (i + key2pos(dest)[1]) * (100/9);
      if (orientation === 'sente') top = (9 - (i + key2pos(dest)[1])) * (100/9);
      return h(
        'square',
        {
          attrs: {
            style: `top:${top}%;left:${left}%;display:table;`,
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

export function view(ctrl: RoundController): MaybeVNode {
  if (!promoting) return;
  const roles: cg.Role[] =
    ctrl.shogiground.state.orientation === 'gote'
      ? [sPromote(promoting.role), promoting.role]
      : [promoting.role, sPromote(promoting.role)];
  return renderPromotion(ctrl, promoting.move[1], roles, ctrl.data.player.color, ctrl.shogiground.state.orientation);
}
