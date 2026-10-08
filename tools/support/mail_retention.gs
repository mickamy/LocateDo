// Moves support conversations to the trash once their last message is older than DAYS_KEPT. Gmail empties the trash
// after 30 days, so every conversation is gone within a year of its last message, as the privacy policy says.

const ADDRESSES = ['support@locatedo.com', 'privacy@locatedo.com'];
const DAYS_KEPT = 330;
const BATCH = 100;

function purgeOldSupportMail() {
  const cutoff = new Date(Date.now() - DAYS_KEPT * 24 * 60 * 60 * 1000);
  const addresses = ADDRESSES.map((address) => `to:${address} OR from:${address} OR deliveredto:${address}`).join(' OR ');
  const query = `{${addresses}} older_than:${DAYS_KEPT}d -in:trash`;
  let trashed = 0;
  let start = 0;
  for (;;) {
    const threads = GmailApp.search(query, start, BATCH);
    if (threads.length === 0) {
      break;
    }
    const expired = threads.filter((thread) => thread.getLastMessageDate() < cutoff);
    if (expired.length > 0) {
      GmailApp.moveThreadsToTrash(expired);
      trashed += expired.length;
    }
    start += threads.length - expired.length;
  }
  console.log(`Moved ${trashed} support conversations to the trash (last message before ${cutoff.toISOString()}).`);
}

function installDailyTrigger() {
  for (const trigger of ScriptApp.getProjectTriggers()) {
    if (trigger.getHandlerFunction() === 'purgeOldSupportMail') {
      ScriptApp.deleteTrigger(trigger);
    }
  }
  ScriptApp.newTrigger('purgeOldSupportMail').timeBased().everyDays(1).atHour(4).create();
}
