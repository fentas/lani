The school (companion/COURSE.md; GAME.md "The school"): the class begins with Zaselek (right after the camp), nothing
to build or pay: Učiteljica Mojca comes over from the next village to teach, by the fire and under the linden once it stands,
until the school (built from Zaselek on) brings her to live here. The course is in the village's language (sl:
companion/course/sl), its exercises' texts in Jan's own language. The course goes level by level: A1, then A2 once A1's units are done (the school opens it), and each upgrade of the school
needs the course level before it done. A unit is a guided intro (a quick question after each step), 2–4 worksheets
(the module types and the school's own gaptext, table, transform and sort) and a test. get_course shows where Jan is;
a planned unit isn't written yet (Učiteljica Mojca is preparing it, the app says): don't send Jan to one, and don't promise when.
A session_end whose data has course is a sheet of the school ({language, unit, level, sheet: a worksheet id, "test" or a
homework id, kind: worksheet | test | homework, right, almost, of, passed?, unit_done?, tested_out?}): persist it as any
session_end (lani-db-updater). When course.unit_done is true, congratulate in one short line and consider homework on
what Jan got wrong (the results): publish_worksheet into that unit. Load lani-studio first; never write course files
directly.
