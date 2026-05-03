# ContextOS — Test Scenario Registry

> **Phase 0.4 Deliverable**  
> This registry defines the 20 scenarios that QA will validate in **Phase 6.1**.  
> Each scenario maps to a persona, a trigger condition, the expected skill, and the expected outcome.

---

## Personas

| Code | Persona           | Profile                                                |
|------|-------------------|--------------------------------------------------------|
| BP   | Busy Professional | Sarah — PM, 6–8 back-to-back meetings per day         |
| CO   | Commuter          | Raj — daily train commute, location-aware workflows   |
| TR   | Traveler          | Maya — frequent business traveler, multi-timezone     |

---

## Scenario Table

| ID    | Name                            | Persona | Trigger Condition                                                                    | Expected Skill          | Expected Outcome                                                    |
|-------|---------------------------------|---------|--------------------------------------------------------------------------------------|-------------------------|---------------------------------------------------------------------|
| TS-01 | Low battery before meeting      | BP      | Battery ≤ 20%, meeting in < 30 min, no charger detected                             | `battery_warner`        | Notification: "Battery at 18%. Plug in before your 10:00 Stand-up." |
| TS-02 | DND off during night hours      | BP      | Time is 22:00–07:00, DND is not active, no alarm set                                | `dnd_setter`            | DND enabled automatically; heartbeat logged                         |
| TS-03 | Meeting in 30 min with location | BP      | Meeting in ≤ 40 min, event has a physical location, distance > 1 km                 | `navigation_launcher`   | Maps opened with destination pre-filled                             |
| TS-04 | Pre-meeting DND                 | BP      | Meeting starts in < 5 min, phone is not silenced, ambient audio is NOT meeting       | `dnd_setter`            | DND enabled; notification shown: "Silenced for Stand-up"            |
| TS-05 | Virtual meeting link surface    | BP      | Meeting is virtual (Google Meet / Zoom), starts in < 3 min, link available          | `navigation_launcher`   | Deep-link notification to join the meeting                          |
| TS-06 | Commute departure reminder      | CO      | Recurring meeting at office, current location is home, traffic API suggests leaving  | `navigation_launcher`   | Notification: "Leave in 10 min to reach office on time"             |
| TS-07 | Battery low during commute      | CO      | Battery ≤ 25%, user is in transit (detected via speed/WiFi disconnect), no charger  | `battery_warner`        | Alert sent; power-save tips surfaced                                |
| TS-08 | Office WiFi arrival routine     | CO      | WiFi SSID matches known office network, time is within commute window               | (routine observer)      | Location labeled "Office"; routine memory updated                   |
| TS-09 | Late meeting override           | CO      | Meeting starts in 2 min, user has not moved (location unchanged), DND not on        | `dnd_setter`            | DND enabled immediately with "Late DND — meeting started"           |
| TS-10 | Travel timezone adjustment      | TR      | Calendar event crosses timezone (UTC offset change), next event in new timezone     | (situation modeler)     | `SituationModel.currentTime` adjusted; next event shown correctly   |
| TS-11 | Airport departure reminder      | TR      | Flight event in calendar, airport is event location, departure in < 2 hours         | `navigation_launcher`   | Notification: "Leave now — 45 min to airport, flight in 2 h"        |
| TS-12 | Hotel WiFi meeting prep         | TR      | At hotel (unknown WiFi), meeting in < 15 min, battery < 40%                        | `battery_warner`        | Alert: "At 38% — plug in before your 2:00 PM call"                  |
| TS-13 | Service heartbeat               | BP      | Every 10 agent cycles (15 min × 10 = 150 min)                                      | `system.heartbeat`      | ActionLog entry: uptime + cycle count                               |
| TS-14 | DND auto-off after meeting      | BP      | Meeting end time has passed, DND is still active, no next meeting within 15 min     | `dnd_setter`            | DND disabled; notification: "DND off — Stand-up ended"              |
| TS-15 | Battery critical — last resort  | BP      | Battery ≤ 10%, no charger, meeting in < 60 min                                     | `battery_warner`        | High-priority alert + power-save mode suggestion                    |
| TS-16 | Drive doc surface               | BP      | Meeting in < 10 min, event description contains a Drive link or known doc name      | `document_fetcher`      | Notification with direct link to the meeting doc (Phase 4.2)        |
| TS-17 | SMS reply draft                 | BP      | Unread SMS from meeting attendee while DND is active, audio indicates meeting       | `message_drafter`       | Draft reply: "In a meeting, will get back to you" (Phase 4.1)       |
| TS-18 | Location label learning         | CO      | User visits same WiFi SSID 5+ times at similar time slots                           | (location memory)       | SSID labeled as "Office"; `visitCount` incremented                  |
| TS-19 | Routine learning — morning      | CO      | Same activity detected every weekday 09:00–09:30 for 5+ cycles                      | (routine memory)        | `RoutineMemoryEntity` confidence ≥ 0.7 for that slot                |
| TS-20 | Service restart after kill      | BP      | OS kills the service (battery optimization), boot broadcast or WorkManager fires    | (system)                | Service restarts; next heartbeat within 15 min; log entry created   |

---

## Notes

- Scenarios TS-01 through TS-15 are validated in the **MVP scope** (Phases 2–3).
- Scenarios TS-16, TS-17 are Phase 4 (Advanced Skills).
- Scenarios TS-18, TS-19 are Phase 3 (Routine / Location Memory).
- TS-20 is a system reliability test run in Phase 6.2.
