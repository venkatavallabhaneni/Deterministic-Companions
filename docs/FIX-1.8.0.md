# Guard ranged combat and route interactions — 1.8.0

Protect and rectangular guard duty now assign exactly half of the selected area guards to ranged duty. Ranged-duty companions receive a real Power V Infinity bow and one arrow, lead moving ranged hostile mobs, draw for the normal vanilla duration, and release through the server `BowItem` path. The other guards retain diamond-sword duty.

Navigation now emits interaction edges for hand-operated doors, levers, and buttons. Interactions temporarily empty the main hand so a held block or sword cannot consume the click. A route that remains stalled in water is invalidated and replanned with the blocked edge suppressed for a short period.
