import { Controller, Puzzle, PuzzleGame, MaybeVNode, PuzzleDifficulty, PuzzlePlayer } from '../interfaces';
import { dataIcon, onInsert } from '../util';
import { h } from 'snabbdom';
import { VNode } from 'snabbdom/vnode';

export function puzzleBox(ctrl: Controller): VNode {
  var data = ctrl.getData();
  return h('div.puzzle__side__metas', [
    puzzleInfos(ctrl, data.puzzle),
    data.game.id ? gameInfos(ctrl, data.game, data.puzzle) : sourceInfos(ctrl, data.game),
  ]);
}

function puzzleInfos(ctrl: Controller, puzzle: Puzzle): VNode {
  const isTsume = puzzle.themes.includes('tsume');
  return h(
    'div.infos.puzzle',
    {
      attrs: dataIcon(isTsume ? '7' : '-'),
    },
    [
      h('div', [
        h('p', [
          isTsume ? ctrl.trans.noarg('tsume') + ' ' : '',
          ...ctrl.trans.vdom(
            'puzzleId',
            h(
              'a',
              {
                attrs: { href: `/training/${puzzle.id}` },
              },
              '#' + puzzle.id
            )
          ),
        ]),
        h(
          'p',
          ctrl.trans.vdom(
            'ratingX',
            ctrl.vm.mode === 'play' ? h('span.hidden', ctrl.trans.noarg('hidden')) : h('strong', puzzle.rating)
          )
        ),
        h('p', ctrl.trans.vdom('playedXTimes', h('strong', window.lishogi.numberFormat(puzzle.plays)))),
      ]),
    ]
  );
}

function sourceInfos(ctrl: Controller, game: PuzzleGame): VNode {
  const authorName =
    game.author && game.author.startsWith('http')
      ? h(
          'a',
          {
            attrs: {
              href: `${game.author}`,
              target: '_blank',
            },
          },
          game.author.replace(/(^\w+:|^)\/\//, '')
        )
      : game.author
      ? h('span', game.author)
      : h('span', 'Unknown author');
  return h(
    'div.infos',
    {
      attrs: dataIcon('f'),
    },
    [
      h('div', [
        h('p', ctrl.trans.vdom('puzzleSource', authorName)),
        h('div.source-description', game.description ?? ''),
      ]),
    ]
  );
}

function gameInfos(ctrl: Controller, game: PuzzleGame, puzzle: Puzzle): VNode {
  const gameName = game.clock ? `${game.clock} • ${game.perf!.name}` : `${game.perf!.name}`;
  return h(
    'div.infos',
    {
      attrs: dataIcon(game.perf!.icon),
    },
    [
      h('div', [
        h(
          'p',
          ctrl.trans.vdom(
            'fromGameLink',
            ctrl.vm.mode == 'play'
              ? h('span', gameName)
              : h(
                  'a',
                  {
                    attrs: {
                      href: `/${game.id}/${ctrl.vm.pov}#${puzzle.initialPly}`,
                    },
                  },
                  gameName
                )
          )
        ),
        h(
          'div.players',
          game.players?.map(p =>
            h(
              'div.player.color-icon.is.text.' + p.color,
              p.ai
                ? "Engine level " + p.ai
                : p.userId === 'anon'
                  ? 'Anonymous'
                  : p.userId
                  ? h(
                      'a.user-link.ulpt',
                      {
                        attrs: { href: '/@/' + p.userId },
                      },
                      playerName(p)
                    )
                  : p.name
            )
          )
        ),
      ]),
    ]
  );
}

function playerName(p: PuzzlePlayer) {
  return p.title && p.title != 'BOT' ? [h('span.utitle', p.title), ' ' + p.name] : p.name;
}

export function userBox(ctrl: Controller): VNode {
  const data = ctrl.getData();
  if (!data.user)
    return h('div.puzzle__side__user', [
      h('p', ctrl.trans.noarg('toGetPersonalizedPuzzles')),
      h('button.button', { attrs: { href: '/signup' } }, ctrl.trans.noarg('signUp')),
    ]);
  const diff = ctrl.vm.round?.ratingDiff;
  return h('div.puzzle__side__user', [
    h(
      'div.puzzle__side__user__rating',
      ctrl.trans.vdom(
        'yourPuzzleRatingX',
        h('strong', [
          data.user.rating - (diff || 0),
          ...(diff && diff > 0 ? [' ', h('good.rp', '+' + diff)] : []),
          ...(diff && diff < 0 ? [' ', h('bad.rp', '−' + -diff)] : []),
        ])
      )
    ),
  ]);
}

const difficulties: PuzzleDifficulty[] = ['easiest', 'easier', 'normal', 'harder', 'hardest'];

export function replay(ctrl: Controller): MaybeVNode {
  const replay = ctrl.getData().replay;
  if (!replay) return;
  const i = replay.i + (ctrl.vm.mode == 'play' ? 0 : 1);
  return h('div.puzzle__side__replay', [
    h(
      'a',
      {
        attrs: {
          href: `/training/dashboard/${replay.days}`,
        },
      },
      ['« ', `Replaying ${ctrl.trans.noarg(ctrl.getData().theme.key)} puzzles`]
    ),
    h('div.puzzle__side__replay__bar', {
      attrs: {
        style: `--p:${replay.of ? Math.round((100 * i) / replay.of) : 1}%`,
        'data-text': `${i} / ${replay.of}`,
      },
    }),
  ]);
}

export function config(ctrl: Controller): MaybeVNode {
  const id = 'puzzle-toggle-autonext';
  return h('div.puzzle__side__config', [
    h('div.puzzle__side__config__jump', [
      h('div.switch', [
        h(`input#${id}.cmn-toggle.cmn-toggle--subtle`, {
          attrs: {
            type: 'checkbox',
            checked: ctrl.autoNext(),
          },
          hook: {
            insert: vnode =>
              (vnode.elm as HTMLElement).addEventListener('change', () => ctrl.autoNext(!ctrl.autoNext())),
          },
        }),
        h('label', { attrs: { for: id } }),
      ]),
      h('label', { attrs: { for: id } }, ctrl.trans.noarg('jumpToNextPuzzleImmediately')),
    ]),
    !ctrl.getData().replay && ctrl.difficulty
      ? h(
          'form.puzzle__side__config__difficulty',
          {
            attrs: {
              action: `/training/difficulty/${ctrl.getData().theme.key}`,
              method: 'post',
            },
          },
          [
            h(
              'label',
              {
                attrs: { for: 'puzzle-difficulty' },
              },
              ctrl.trans.noarg('difficultyLevel')
            ),
            h(
              'select#puzzle-difficulty.puzzle__difficulty__selector',
              {
                attrs: { name: 'difficulty' },
                hook: onInsert(elm =>
                  elm.addEventListener('change', () => (elm.parentNode as HTMLFormElement).submit())
                ),
              },
              difficulties.map(diff =>
                h(
                  'option',
                  {
                    attrs: {
                      value: diff,
                      selected: diff == ctrl.difficulty,
                    },
                  },
                  ctrl.trans.noarg(diff)
                )
              )
            ),
          ]
        )
      : null,
  ]);
}
