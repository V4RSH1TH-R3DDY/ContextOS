# ContextOS — User Journey Maps

> **Phase 0.4 Deliverable**  
> Three personas and their full day-in-the-life interactions with ContextOS.

---

## Persona 1 — "The Busy Professional"

**Name:** Sarah, 34  
**Role:** Product Manager at a mid-sized SaaS company  
**Context:** 6–8 back-to-back meetings per day, often forgets to silence her phone or plug in before calls. Relies heavily on Google Calendar and Drive.

### Goals
- Never miss an important notification because her phone is already silent
- Always have the meeting doc open before a call starts
- Keep battery above 20% throughout the workday

### Journey Map

| Step | Action | Emotional State | ContextOS Touchpoint | Pain Without App | Delight With App |
|------|--------|----------------|----------------------|-----------------|-----------------|
| 1 | Arrives at office 9:00 AM | 😐 Rushed | Location inferred as "Office" via WiFi | Has to remember to plug in phone | ContextOS quietly notes she's at the office |
| 2 | 9:55 AM — Stand-up in 5 min | 😰 Stressed | `dnd_setter` fires, phone silenced | Forgets to silence; phone rings mid-standup | Notification: "Silenced for Stand-up. Will re-enable at 10:15." |
| 3 | 10:00 AM — Joining Google Meet | 😤 Multitasking | `document_fetcher` surfaces Meeting Notes doc | Has to hunt through Drive for the agenda | Notification with one-tap link to the pre-meeting doc |
| 4 | 11:50 AM — Battery at 18% | 😟 Worried | `battery_warner` fires | Scrambles to find a charger mid-meeting | Alert: "Battery at 18%. Plug in before your 12:00 PM call." |
| 5 | 2:30 PM — On-site meeting invite | 😅 Distracted | `navigation_launcher` computes travel time | Misses the "leave now" window | Notification: "Leave in 12 min for Koramangala — traffic building up." |
| 6 | 5:00 PM — Workday ends | 😌 Relieved | `dnd_setter` disables DND | Has to remember to re-enable notifications | Phone un-silenced automatically as last meeting ends |
| 7 | 10:30 PM — Night hours | 😴 Sleeping | `dnd_setter` enables night DND | Woken by Slack notifications | DND re-enabled; critical calls still pass through |

---

## Persona 2 — "The Commuter"

**Name:** Raj, 28  
**Role:** Software Engineer at a product company  
**Context:** Daily train commute from Whitefield to Indiranagar (~45 min), attends a daily 9:30 AM stand-up. Location-aware patterns are key.

### Goals
- Never be late to stand-up because he misjudged the commute time
- Have his phone charged before arriving at office
- Reduce repetitive location check-ins

### Journey Map

| Step | Action | Emotional State | ContextOS Touchpoint | Pain Without App | Delight With App |
|------|--------|----------------|----------------------|-----------------|-----------------|
| 1 | 8:15 AM — Leaves home | 😐 Groggy | Home WiFi disconnect → location transitions to "Commuting" | Manually opens Maps every day | ContextOS starts commute-mode context |
| 2 | 8:20 AM — On the train | 😌 Listening to music | `battery_warner` checks level | Battery at 23%, might not last the day | Alert: "Battery at 23%. Charge during commute." |
| 3 | 8:50 AM — Arriving at Indiranagar | 🙂 Alert | Office WiFi detected; location → "Office" | Has to open calendar manually | Routine memory notes arrival time; routine confidence increases |
| 4 | 9:15 AM — 15 min to stand-up | 😶 Focused | `dnd_setter` queued for 9:25 AM | Sometimes leaves phone on vibrate, misses team message | Pre-silence notification at 9:25 AM |
| 5 | 9:30 AM — Stand-up starts | 😤 In the zone | DND active, meeting link surfaced | Scrambles to find the Meet link | Single-tap to join from notification |
| 6 | 6:30 PM — Leaving office | 😌 Done for the day | WiFi disconnect → "Commuting" again | N/A | Context stored; routine observed for departure time |
| 7 | 7:15 PM — Home | 😴 Tired | Home WiFi → "Home" label confirmed | N/A | Daily summary in action log: 4 actions taken today |

---

## Persona 3 — "The Traveler"

**Name:** Maya, 41  
**Role:** Sales Director, travels 2–3 weeks per month  
**Context:** Frequent cross-city/country travel, jet lag, back-to-back client meetings in different time zones. Needs reliable meeting reminders and battery management on the go.

### Goals
- Never miss a meeting because of timezone confusion
- Always know when to leave for the airport
- Have relevant docs and meeting links ready even in unfamiliar locations

### Journey Map

| Step | Action | Emotional State | ContextOS Touchpoint | Pain Without App | Delight With App |
|------|--------|----------------|----------------------|-----------------|-----------------|
| 1 | 5:00 AM — Hotel, preparing for early flight | 😴 Exhausted | Calendar event: "Flight BLR → DEL" detected | Has to manually calculate when to leave | Notification: "Leave by 5:45 AM — 35 min to airport." |
| 2 | 5:30 AM — En route to airport | 😰 Anxious | Battery at 42%; no charger | Could run out before landing | Alert: "Charge before boarding. Battery at 42%." |
| 3 | 8:00 AM — Delhi, first meeting in new timezone | 😕 Disoriented | Timezone offset applied to SituationModel | Miscalculates meeting time | All upcoming events shown in local Delhi time |
| 4 | 10:45 AM — Meeting in 15 min, location is client office | 😤 Rushing | `navigation_launcher` fires | Opens Maps, enters address manually | Notification: "Client office is 12 min away. Leave now." |
| 5 | 11:00 AM — Client meeting | 😅 Composed | `dnd_setter` activates; `document_fetcher` surfaces pitch deck | Hunts for the deck, phone rings | Phone silent; pitch deck link tapped before walking in |
| 6 | 2:00 PM — Return flight in 3 hours | 😌 Wrapping up | Flight event detected again | No reminder to wrap up on time | Notification: "Flight in 3h. Start wrapping up your meeting." |
| 7 | 10:30 PM — Back in Bangalore | 😴 Drained | Location → "Home" (WiFi recognized) | N/A | Night DND activated; action log shows 7 actions taken today |

---

## Cross-Persona Insight

| Pain Point | Frequency | Addressed By |
|---|---|---|
| Phone rings in a meeting | Very High | `dnd_setter` |
| Misses "leave now" window | High | `navigation_launcher` |
| Battery dies before a critical call | High | `battery_warner` |
| Hunts for meeting link / doc | Medium | `document_fetcher` (Phase 4) |
| Misreads meeting time across timezone | Medium | `SituationModelBuilder` time normalization |
| Doesn't know if app is working | Low | `system.heartbeat` visible in Action Log |
