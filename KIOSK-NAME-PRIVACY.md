# Always-on visual name privacy

Name privacy attaches automatically with Turbo Client and remains active while
combat scripts are stopped, observing, learning or running. F9 does not turn it
off. It requires no toggle. The main application banner reports its attach status.

The Orion window title replaces the detected account/character name with block
characters. A topmost game overlay draws opaque black strips over matching text
in visible widgets (including chat), the local player's standard overhead-name
position, and matching names in open context menus. It has no input listeners or
input-catching window and does not change widget text, character identity, clicks,
combat decisions or server state. Existing click targets remain usable beneath it.
Only visual presentation is masked; underlying game data is unchanged.

Names are held in memory from the local player and current login account. No name
list, screenshot or chat content is written by the masking feature. It does not
inspect other desktop applications or obscure other windows. Normal client title
behavior is restored when this companion detaches. Thus privacy coverage requires
the companion to be attached; a full close/reload is not a separate OS-level mask.

Mask positioning uses actual visible widget bounds/font widths and the installed
client's standard overhead-name anchor. Unsupported custom overlays or altered
layouts may need adjustment after a live visual check. Synthetic render tests
verify opaque name coverage while leaving other pixels, game text and identity
unchanged; native Orion title, scaling and every live name location have not yet
been verified in this development session.

The native DPS counter is also covered. Its transient panel text is separate from
game widgets, so a read-only adapter uses the installed DPS member rows and actual
overlay bounds to position the local-name strip. The mask follows panel movement;
damage values and other players' names stay visible. No DPS values or plugin
configuration are changed. Overhead width calculations strip inline markup to
avoid blanking an unnecessarily large area. Installed-class fixture checks pass;
live placement still requires checking after reload.
