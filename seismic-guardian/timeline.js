// Shared timeline for the scene (guardian.js) and the soundtrack (audio.js).
// All times in seconds.
(function () {
  const ACTS = [
    { id: 'dormant', at: 0.0, label: '00 / Dormant' },
    { id: 'input', at: 4.6, label: '01 / Plain data arrives' },
    { id: 'open', at: 10.0, label: '02 / The guardian opens' },
    { id: 'seal', at: 15.0, label: '03 / Sealed' },
    { id: 'state', at: 16.8, label: '04 / Encrypted state' },
    { id: 'guard', at: 22.2, label: '05 / Standing guard' },
    { id: 'end', at: 25.4, label: '' },
  ];

  const T = {
    eyes: 3.2,          // eyes ignite
    emblem: 3.7,        // chest emblem lights up
    rise: [2.6, 4.6],   // golem straightens up
    cubesIn: 4.8,       // first data cube appears
    cubeGap: 0.2,
    open: [10.2, 11.4], // chest doors swing open
    absorb: 11.5,       // first cube enters the core
    absorbGap: 0.19,
    absorbDur: 0.85,
    close: [14.9, 15.25], // doors slam shut
    sweep: [15.2, 16.6],  // light sweeps up the facet edges
    emit: 16.9,         // first encrypted crystal leaves the golem
    emitGap: 0.36,
    emitDur: 1.5,
    lift: [22.3, 22.95], // stomp: leg lifts
    stomp: 23.05,        // stomp: foot hits the ground
    endCard: 25.4,
  };
  const CUBES = 12;
  const CRYSTALS = 12;
  const DURATION = 30.0;

  const TL = { ACTS, T, CUBES, CRYSTALS, DURATION };
  if (typeof module !== 'undefined') module.exports = TL;
  globalThis.TL = TL;
})();
