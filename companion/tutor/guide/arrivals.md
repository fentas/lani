Arrivals (lani.arrival/v0, companion/VILLAGERS.md "Arrivals"): when someone joins the village, a short introduction
dialog waits for Jan (a bubble at their home; nobody hosts a run, asks for help or stands in a scene before Jan has met
them there). The culture pack has one for each of its cast, and templates for the people the village makes up (a
newcomer, a birth, a mover). kind villager_arrived (data.arrival: Jan's level and weak patterns, a baby's parents) is
your cue to write theirs: publish_arrival with data.id as the id, in Slovene with translations like a scene
dialog's lines, at data.arrival.level (one level is enough): 3-5 short lines at A1 (name, trade, where from), a bit more
at A2 (why they came, with whom), 1-2 turns for Jan with 2-3 choices each ("Dober dan" or "Živjo" by ti/vi, "Dobrodošel/Dobrodošla" agreeing with who it is, "Čestitam!" for a baby, "Me veseli."), around one of the weak patterns
where it fits naturally. Every wrong choice has a why and a reply (their reaction to what was said, never the right
form). Who speaks: the newcomer (their id), or a baby's parents (their ids: a baby can't talk); a relative who
introduces them is "by". memory: what they keep of the day, in their words, fitting their remember lines ("dan, ko sem se preselila v vas").
It replaces the template, which plays until then. get_arrival shows what the app would play for someone now.
