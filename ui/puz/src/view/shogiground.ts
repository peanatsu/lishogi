import changeColorHandle from 'common/coordsColor';
import resizeHandle from 'common/resize';
import { Config as CgConfig } from 'shogiground/config';
import { PuzPrefs, UserDrop, UserMove } from '../interfaces';

export function makeConfig(opts: CgConfig, pref: PuzPrefs, userMove: UserMove, userDrop: UserDrop, redraw: () => void): CgConfig {
  return {
    fen: opts.fen,
    orientation: opts.orientation,
    turnColor: opts.turnColor,
    check: opts.check,
    lastMove: opts.lastMove,
    coordinates: pref.coords !== 0,
    addPieceZIndex: pref.is3d,
    movable: {
      free: false,
      color: opts.movable!.color,
      dests: opts.movable!.dests,
      showDests: pref.destination,
    },
    draggable: {
      enabled: pref.moveEvent > 0,
      showGhost: pref.highlight,
    },
    selectable: {
      enabled: pref.moveEvent !== 1,
    },
    events: {
      move: userMove,
      dropNewPiece: userDrop,
      insert(elements) {
        resizeHandle(elements, 1, 0, p => p == 0);
        if (pref.coords == 1) changeColorHandle();
      },
      select: () => {
        if (!opts.dropmode?.active) {
          redraw();
        }
      },
    },
    premovable: {
      enabled: false,
    },
    predroppable: {
      enabled: false,
    },
    dropmode: {
      dropDests: opts.dropmode!.dropDests,
      showDropDests: pref.destination && pref.dropDestination,
    },
    drawable: {
      enabled: true,
    },
    highlight: {
      lastMove: pref.highlight,
      check: pref.highlight,
    },
    animation: {
      enabled: false,
    },
    disableContextMenu: true,
  };
}
