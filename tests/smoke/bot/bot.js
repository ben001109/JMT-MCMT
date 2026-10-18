// Joins the smoke-test server as an offline-mode player, wanders a little, and leaves after
// --seconds. Exit codes: 0 stayed connected the whole time, 1 kicked/errored/never spawned.
//
//   node bot.js --host 127.0.0.1 --port 25565 --seconds 60 --name MCMTBot
import mineflayer from 'mineflayer';

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, arr) => {
    if (a.startsWith('--')) acc.push([a.slice(2), arr[i + 1]]);
    return acc;
  }, [])
);
const host = args.host ?? '127.0.0.1';
const port = Number(args.port ?? 25565);
const seconds = Number(args.seconds ?? 60);
const name = args.name ?? 'MCMTBot';

const log = (...m) => console.log(`[bot ${new Date().toISOString()}]`, ...m);

let spawned = false;
let failed = false;

const bot = mineflayer.createBot({ host, port, username: name, auth: 'offline', version: '1.21.1' });

bot.once('spawn', () => {
  spawned = true;
  log('spawned at', bot.entity.position.toString());
  bot.chat('MCMT smoke bot online');

  // Light activity so the server has a moving player: look around, hop, nudge forward.
  let step = 0;
  const activity = setInterval(() => {
    if (!bot.entity) return;
    step++;
    const yaw = (step % 8) * (Math.PI / 4);
    bot.look(yaw, 0, true).catch(() => {});
    if (step % 3 === 0) {
      bot.setControlState('jump', true);
      setTimeout(() => bot.setControlState('jump', false), 300);
    }
    if (step % 5 === 0) {
      bot.setControlState('forward', true);
      setTimeout(() => bot.setControlState('forward', false), 600);
    }
    if (step % 10 === 0) log('still here at', bot.entity.position.floored().toString(), 'health', bot.health);
  }, 1000);

  setTimeout(() => {
    clearInterval(activity);
    log('leaving after', seconds, 'seconds');
    bot.chat('MCMT smoke bot leaving');
    setTimeout(() => { bot.quit(); }, 500);
  }, seconds * 1000);
});

bot.on('kicked', (reason) => {
  const text = JSON.stringify(reason);
  // Being kicked by the final /stop is fine; anything else is a failure.
  if (!text.includes('server_shutdown')) failed = true;
  log(failed ? 'kicked:' : 'kicked at shutdown:', text);
});
bot.on('error', (err) => { failed = true; log('error:', err?.message ?? err); });
bot.on('end', (reason) => {
  log('disconnected:', reason);
  process.exit(failed || !spawned ? 1 : 0);
});

// Safety net: never hang forever.
setTimeout(() => { log('timeout waiting for spawn/leave'); process.exit(spawned && !failed ? 0 : 1); }, (seconds + 120) * 1000);
