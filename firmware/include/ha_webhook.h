#pragma once

#ifdef ENABLE_HA_WEBHOOK

namespace HaWebhook {

void init();
void handle();

} // namespace HaWebhook

#endif // ENABLE_HA_WEBHOOK
