# ONE-OFF (it-print-meanings, 2026-11-01): these scripts made the first version of the Italian course's entries in
# companion/lexicon/it.extra.json (271 entries after its first 38). That file is the source now: edit it by hand, as the
# rest of the supplement. Kept so the way the forms were made can be read and run again for new words; a run adds only
# entries the file doesn't have (it_extra_merge.py), never changes one. See docs/plans/05-built/it-print-meanings.md.
#
# The Italian words the course's sheets left off (no meaning in the packs), written for Teo: German first ("du"),
# Slovene and English. Machine-written (Claude), checked against Wiktionary's build (kaikki) for the forms and the
# senses. One entry a line:  lemma | pos | gender | paradigm | en ;; en2 | de ;; de2 | sl ;; sl2
# paradigm: v (a verb, regular by its ending; -rsi reflexive), vi (-ire with -isc-), v:<irregular key>,
#           n (a noun, its plural by the ending), n:<plural>[/<plural2>], n= (no other form), a (adj -o), a2 (adj -e),
#           a:bello, p:<verb>:<rest> (a phrase made of a verb's forms and the rest), - (only the word itself)
ENTRIES = r"""
abitare | verb | | v | to live (somewhere) | wohnen | stanovati, živeti
addormentarsi | verb | | v | to fall asleep | einschlafen | zaspati
andare | verb | | v:andare | to go | gehen; fahren | iti
apparecchiare | verb | | v | to lay the table | den Tisch decken | pogrniti mizo
aprirsi | verb | | v:aprire | to open (by itself) | aufgehen, sich öffnen | odpreti se
arrivare | verb | | v | to arrive, to come | ankommen | priti, prispeti
ascoltare | verb | | v | to listen (to) | zuhören, hören | poslušati
avere | verb | | v:avere | to have | haben | imeti
avvicinarsi | verb | | v | to come closer | näher kommen | približati se
bastare | verb | | v | to be enough | reichen, genug sein | zadostovati
bisognare | verb | | v | one must, it is necessary (bisogna) | man muss, es ist nötig | treba je (bisogna)
bussare | verb | | v | to knock | klopfen, anklopfen | potrkati
capire | verb | | vi | to understand | verstehen | razumeti
chiamarsi | verb | | v | to be called (mi chiamo: my name is) | heißen (mi chiamo: ich heiße) | imenovati se (mi chiamo: ime mi je)
chiedere | verb | | v:chiedere | to ask | fragen; bitten | vprašati; prositi
cominciare | verb | | v | to begin, to start | anfangen, beginnen | začeti
conoscere | verb | | v:conoscere | to know (a person, a place) | kennen | poznati
cuocere | verb | | v:cuocere | to cook, to bake | kochen, backen | kuhati, peči
dare | verb | | v:dare | to give | geben | dati
dimenticare | verb | | v | to forget | vergessen | pozabiti
dire | verb | | v:dire | to say, to tell | sagen | reči, povedati
divertirsi | verb | | v | to have fun | Spaß haben | zabavati se
dovere | verb | | v:dovere | to have to, must | müssen | morati
essere | verb | | v:essere | to be | sein | biti
fare | verb | | v:fare | to do, to make | machen, tun | delati
finire | verb | | vi | to finish, to end | fertig machen; enden, aufhören | končati
funzionare | verb | | v | to work (a machine) | funktionieren | delovati
girarsi | verb | | v | to turn round | sich umdrehen | obrniti se
gridare | verb | | v | to shout | schreien, rufen | kričati
guardare | verb | | v | to look (at), to watch | schauen, anschauen | gledati
incontrare | verb | | v | to meet | treffen | srečati
lavarsi | verb | | v | to wash (yourself) | sich waschen | umivati se
lavorare | verb | | v | to work | arbeiten | delati
mandare | verb | | v | to send | schicken | poslati
mettere | verb | | v:mettere | to put; to put on (clothes) | legen, stellen; anziehen | dati, postaviti; obleči
mordere | verb | | v:mordere | to bite | beißen | ugrizniti
mostrare | verb | | v | to show | zeigen | pokazati
nevicare | verb | | v | to snow | schneien | snežiti
parlare | verb | | v | to speak, to talk | sprechen, reden | govoriti
partire | verb | | v | to leave, to set off | abfahren, losfahren | oditi, odpotovati
pensare | verb | | v | to think | denken | misliti
pettinarsi | verb | | v | to comb your hair | sich kämmen | česati se
piovere | verb | | v:piovere | to rain | regnen | deževati
pizzicare | verb | | v | to pinch | zwicken | ščipati
potere | verb | | v:potere | can, to be able to; may | können; dürfen | moči; smeti
preferire | verb | | vi | to prefer | lieber mögen | imeti raje
prendere | verb | | v:prendere | to take | nehmen | vzeti
preparare | verb | | v | to prepare, to make | vorbereiten, zubereiten | pripraviti
prestare | verb | | v | to lend | leihen, ausleihen | posoditi
raccontare | verb | | v | to tell (a story) | erzählen | pripovedovati
regalare | verb | | v | to give (as a present) | schenken | podariti
restare | verb | | v | to stay | bleiben | ostati
ripetere | verb | | v | to repeat | wiederholen | ponoviti
sapere | verb | | v:sapere | to know; can (know how to) | wissen; können | vedeti; znati
scappare | verb | | v | to run away | weglaufen | pobegniti
scoprire | verb | | v:scoprire | to discover, to find out | entdecken, herausfinden | odkriti
sedersi | verb | | v:sedere | to sit down | sich hinsetzen | usesti se
sparire | verb | | vi | to disappear | verschwinden | izginiti
sperare | verb | | v | to hope | hoffen | upati
sposarsi | verb | | v | to get married | heiraten | poročiti se
stare | verb | | v:stare | to be; to stay (come stai?: how are you?) | sein; bleiben (come stai?: wie geht's dir?) | biti; ostati (come stai?: kako si?)
telefonare | verb | | v | to phone, to call | anrufen, telefonieren | poklicati, telefonirati
tenere | verb | | v:tenere | to hold; to keep | halten; behalten | držati; obdržati
toccare | verb | | v | to touch | anfassen, berühren | dotakniti se
tornare | verb | | v | to come back, to go back | zurückkommen | vrniti se
usare | verb | | v | to use | benutzen | uporabljati
venire | verb | | v:venire | to come | kommen | priti
vestirsi | verb | | v | to get dressed | sich anziehen | oblačiti se
vivere | verb | | v:vivere | to live | leben | živeti
volerci | verb | | p:volere:^ci | to take, to be needed (ci vuole un'ora: it takes an hour) | nötig sein, dauern (ci vuole un'ora: es dauert eine Stunde) | biti potreben, trajati (ci vuole un'ora: traja eno uro)
volere | verb | | v:volere | to want | wollen | hoteti
andare via | phrase | | p:andare:via | to go away, to leave | weggehen | oditi, iti proč
andare in bagno | phrase | | p:andare:in bagno | to go to the toilet | auf die Toilette gehen | iti na stranišče
andare in bicicletta | phrase | | p:andare:in bicicletta | to ride a bike | Fahrrad fahren | voziti kolo
fare il bagno | phrase | | p:fare:il bagno | to have a bath; to swim (in the sea) | baden | kopati se
anno | noun | m | n | year | Jahr | leto
biscotto | noun | m | n | biscuit, cookie | Keks | piškot
capello | noun | m | n | hair (a single one; i capelli: hair) | Haar | las (i capelli: lasje)
colore | noun | m | n | colour | Farbe | barva
fico | noun | m | n:fichi | fig | Feige | smokva, figa
fumetto | noun | m | n | comic | Comic | strip
ragno | noun | m | n | spider | Spinne | pajek
bastone | noun | m | n | stick | Stock | palica
biglietto | noun | m | n | card, note; ticket | Karte, Zettel; Fahrkarte | voščilnica, listek; vozovnica
calciatore | noun | m | n | footballer | Fußballspieler | nogometaš
cappotto | noun | m | n | coat | Mantel | plašč
cavaliere | noun | m | n | knight | Ritter | vitez
compito | noun | m | n | homework (i compiti); task | Hausaufgabe; Aufgabe | domača naloga; naloga
compleanno | noun | m | n | birthday | Geburtstag | rojstni dan
consiglio | noun | m | n:consigli | advice, a tip | Rat, Tipp | nasvet
corridoio | noun | m | n:corridoi | corridor, hallway | Flur, Gang | hodnik
delfino | noun | m | n | dolphin | Delfin | delfin
disegno | noun | m | n | drawing | Zeichnung, Bild | risba
forno | noun | m | n | oven | Backofen | pečica
freddo | noun | m | - | the cold (avere freddo: to be cold) | Kälte (avere freddo: frieren) | mraz (avere freddo: zebsti)
frigo | noun | m | n= | fridge | Kühlschrank | hladilnik
gattino | noun | m | n | kitten | Kätzchen | mucek
giornale | noun | m | n | newspaper | Zeitung | časopis
giro | noun | m | n | ride, round (fare un giro: go for a ride) | Runde (fare un giro: eine Runde drehen) | krog (fare un giro: iti na krog)
ladro | noun | m | n | thief | Dieb | tat
leone | noun | m | n | lion | Löwe | lev
maglione | noun | m | n | jumper, sweater | Pullover | pulover
mal di schiena | noun | m | - | backache | Rückenschmerzen | bolečina v hrbtu
mese | noun | m | n | month | Monat | mesec
montasio | noun | m | - | Montasio (a cheese from Friuli) | Montasio (Käse aus Friaul) | montasio (sir iz Furlanije)
panino | noun | m | n | bread roll, sandwich | Brötchen, belegtes Brot | sendvič, žemlja
pascolo | noun | m | n | pasture | Weide | pašnik
pettine | noun | m | n | comb | Kamm | glavnik
pezzo | noun | m | n | piece | Stück | kos
porcellino | noun | m | n | piglet | Ferkel, Schweinchen | pujsek
porto | noun | m | n | harbour | Hafen | pristanišče
principe | noun | m | n | prince | Prinz | princ
recinto | noun | m | n | pen, fence (for animals) | Gehege, Zaun | ograda, staja
segreto | noun | m | n | secret | Geheimnis | skrivnost
sonno | noun | m | - | sleepiness (avere sonno: to be sleepy) | Müdigkeit (avere sonno: müde sein) | zaspanost (avere sonno: biti zaspan)
tamburo | noun | m | n | drum | Trommel | boben
torrente | noun | m | n | mountain stream | Wildbach, Bach | hudournik, potok
treno | noun | m | n | train | Zug | vlak
Epifania | noun | f | - | Epiphany (6 January) | Dreikönigstag (6. Januar) | sveti trije kralji (6. januar)
agnellino | noun | m | n | little lamb | Lämmchen | jagenjček
aranciata | noun | f | n | orangeade, orange soda | Orangenlimonade | oranžada
indovinello | noun | m | n | riddle | Rätsel | uganka
inverno | noun | m | n | winter | Winter | zima
oro | noun | m | - | gold | Gold | zlato
osso | noun | m | n:ossa/ossi | bone | Knochen | kost
bambola | noun | f | n | doll | Puppe | punčka
bici | noun | f | n= | bike | Fahrrad | kolo
borsa | noun | f | n | bag, handbag | Tasche, Handtasche | torba, torbica
candelina | noun | f | n | little candle (on a cake) | Kerze (auf dem Kuchen) | svečka
carta | noun | f | n | paper; card | Papier; Karte | papir; karta
cartolina | noun | f | n | postcard | Postkarte | razglednica
cipolla | noun | f | n | onion | Zwiebel | čebula
classe | noun | f | n | class, classroom | Klasse | razred
corsa | noun | f | n | race, run | Wettlauf, Rennen | tek, dirka
fata | noun | f | n | fairy | Fee | vila
fetta | noun | f | n | slice | Scheibe, Stück | rezina, kos
fisarmonica | noun | f | n:fisarmoniche | accordion | Akkordeon, Ziehharmonika | harmonika
formica | noun | f | n:formiche | ant | Ameise | mravlja
frase | noun | f | n | sentence | Satz | stavek
fucina | noun | f | n | forge, smithy | Schmiede | kovačnica
gente | noun | f | - | people | Leute | ljudje
giornata | noun | f | n | day (all day long) | Tag (der ganze Tag) | dan
lana | noun | f | - | wool | Wolle | volna
lettera | noun | f | n | letter | Brief; Buchstabe | pismo; črka
luce | noun | f | n | light | Licht | luč, svetloba
magia | noun | f | n | magic, a spell | Zauber | čarovnija
maglia | noun | f | n | top, jumper | Pulli, Oberteil | majica, pulover
moneta | noun | f | n | coin | Münze | kovanec
padella | noun | f | n | frying pan | Pfanne | ponev
partita | noun | f | n | match, game | Spiel, Match | tekma
pasticceria | noun | f | n | cake shop | Konditorei | slaščičarna
paura | noun | f | n | fear (avere paura: to be afraid) | Angst (avere paura: Angst haben) | strah (avere paura: bati se)
pazienza | noun | f | - | patience | Geduld | potrpljenje
pozzanghera | noun | f | n | puddle | Pfütze | luža
principessa | noun | f | n | princess | Prinzessin | princesa
risposta | noun | f | n | answer | Antwort | odgovor
salita | noun | f | n | climb, uphill road | Steigung, Aufstieg | vzpon, klanec
scatola | noun | f | n | box | Schachtel, Kiste | škatla
sete | noun | f | - | thirst (avere sete: to be thirsty) | Durst (avere sete: Durst haben) | žeja (avere sete: biti žejen)
signora | noun | f | n | lady; Mrs | Frau; Dame | gospa
signore | noun | m | n:signori/signor@singular (before a name) | gentleman; Mr (signor Franco) | Herr | gospod
spada | noun | f | n | sword | Schwert | meč
stazione | noun | f | n | station | Bahnhof | postaja
strega | noun | f | n:streghe | witch | Hexe | čarovnica
tasca | noun | f | n:tasche | pocket | Tasche (an der Hose) | žep
valigia | noun | f | n:valigie | suitcase | Koffer | kovček
veterinaria | noun | f | n | vet (a woman) | Tierärztin | veterinarka
briciola | noun | f | n | crumb | Krümel | drobtina
uomo | noun | m | n:uomini | man | Mann | moški
scoglio | noun | m | n:scogli | rock (by the sea) | Klippe, Felsen (im Meer) | čer, skala
stivale | noun | m | n | boot | Stiefel | škorenj
zucchero | noun | m | - | sugar | Zucker | sladkor
domenica | noun | f | n:domeniche | Sunday (la domenica: on Sundays) | Sonntag (la domenica: sonntags) | nedelja (la domenica: ob nedeljah)
sabato | noun | m | n | Saturday | Samstag | sobota
attento | adj | | a | careful (stai attento!: be careful!) | vorsichtig (stai attento!: pass auf!) | pazljiv (stai attento!: pazi!)
azzurro | adj | | a | light blue, sky blue | hellblau | sinji, svetlo moder
bello | adj | | a:bello | beautiful, nice | schön | lep
calmo | adj | | a | calm | ruhig | miren
contento | adj | | a | glad, happy | froh | vesel, zadovoljen
felice | adj | | a2 | happy | glücklich | srečen
gentile | adj | | a2 | kind | nett, freundlich | prijazen
grande | adj | | a2:gran | big; grown-up | groß | velik
importante | adj | | a2 | important | wichtig | pomemben
lento | adj | | a | slow | langsam | počasen
migliore | adj | | a2:miglior | better; best (il migliore) | besser; der beste | boljši; najboljši
peggiore | adj | | a2:peggior | worse; worst (il peggiore) | schlechter; der schlechteste | slabši; najslabši
nuovo | adj | | a | new | neu | nov
piccolo | adj | | a | small, little | klein | majhen
triste | adj | | a2 | sad | traurig | žalosten
vero | adj | | a | true, real | wahr, echt | resničen, pravi
zitto | adj | | a | quiet (stai zitto!: be quiet!) | still (stai zitto!: sei still!) | tiho (stai zitto!: bodi tiho!)
solo | adj | | a | alone | allein | sam
primo | adj | | a | first | erster | prvi
il primo | phrase | | - | the first (il primo agosto: the first of August) | der Erste (il primo agosto: der 1. August) | prvi (il primo agosto: 1. avgust)
tanto | adj | | a | so much, so many; a lot | so viel, so viele | toliko, veliko
tutto | adj | | a | all, the whole; everything | ganz, alle; alles | ves, cel; vse
tutti | pron | | - | everyone, all | alle | vsi
quanto | adj | | a | how much, how many; as … as (bravo quanto) | wie viel; so … wie | koliko; tako … kot
mio | det | | a:mio | my | mein | moj
tuo | det | | a:tuo | your | dein | tvoj
nostro | det | | a | our | unser | naš
vostro | det | | a | your (you all) | euer | vaš
ogni | det | | - | every, each | jeder, jede, jedes | vsak
nessuno | pron | | a:nessuno | nobody; no (nessun) | niemand; kein | nihče; noben
niente | pron | | - | nothing | nichts | nič
qualcosa | pron | | - | something | etwas | nekaj
qualcuno | pron | | a:qualcuno | someone, somebody | jemand | nekdo
glielo | pron | | - | it to him, it to her (gli + lo) | es ihm, es ihr (gli + lo) | to njemu, to njej (gli + lo)
gliela | pron | | - | it to him, it to her (gli + la) | sie ihm, sie ihr (gli + la) | to njemu, to njej (gli + la)
glieli | pron | | - | them to him, to her (gli + li) | sie ihm, sie ihr (gli + li) | jih njemu, njej (gli + li)
gliele | pron | | - | them to him, to her (gli + le) | sie ihm, sie ihr (gli + le) | jih njemu, njej (gli + le)
gliene | pron | | - | some of it to him, to her (gli + ne) | davon ihm, ihr (gli + ne) | od tega njemu, njej (gli + ne)
molto | adv | | a | very; a lot (molti: many) | sehr; viel (molti: viele) | zelo; veliko (molti: mnogi)
adesso | adv | | - | now | jetzt | zdaj
allora | adv | | - | then; so | dann; also | takrat; torej
altrimenti | adv | | - | otherwise | sonst | sicer, drugače
anche | adv | | - | also, too | auch | tudi
ancora | adv | | - | still; again; yet (non ancora) | noch; wieder | še; spet
appena | adv | | - | as soon as; just | sobald; gerade | brž ko; ravnokar
benissimo | adv | | - | very well, great | sehr gut | zelo dobro
certo | adv | | - | of course, sure | klar, natürlich | seveda
come | adv | | - | how; like, as | wie | kako; kot
dopo | adv | | - | after; later | nach; danach, später | po; potem, pozneje
forse | adv | | - | maybe | vielleicht | morda
già | adv | | - | already | schon | že
lì | adv | | - | there | dort, da | tam
mai | adv | | - | never (non … mai); ever | nie (non … mai); jemals | nikoli (non … mai); kdaj
meglio | adv | | - | better | besser | bolje
più | adv | | - | more; most (non … più: no more) | mehr (non … più: nicht mehr) | bolj, več (non … più: nič več)
poi | adv | | - | then, later | dann, danach | potem
prima | adv | | - | before; first | vorher; zuerst | prej; najprej
proprio | adv | | - | really; just, right (proprio adesso: right now) | wirklich; genau (proprio adesso: genau jetzt) | res; ravno (proprio adesso: ravno zdaj)
qui | adv | | - | here | hier | tukaj
quando | adv | | - | when | wann; wenn, als | kdaj; ko
sempre | adv | | - | always | immer | vedno
stamattina | adv | | - | this morning | heute Morgen | danes zjutraj
stanotte | adv | | - | tonight; last night | heute Nacht | nocoj; to noč
stasera | adv | | - | this evening, tonight | heute Abend | nocoj, danes zvečer
subito | adv | | - | at once, right away | sofort | takoj
volentieri | adv | | - | gladly, with pleasure | gern | z veseljem, rad
via | adv | | - | away (andare via: to go away) | weg (andare via: weggehen) | proč (andare via: oditi)
mentre | conj | | - | while | während | medtem ko
con | prep | | - | with | mit | z, s
senza | prep | | - | without | ohne | brez
prima di | prep | | - | before (prima di dormire: before sleeping) | vor (prima di dormire: vor dem Schlafen) | pred (prima di dormire: pred spanjem)
zero | num | | - | zero | null | nič
basta | intj | | - | enough!, stop!; basta che: as long as | genug!, es reicht; basta che: Hauptsache | dovolj!; basta che: samo da
basta che | phrase | | - | as long as, provided that | Hauptsache, wenn nur | samo da, le da
dai | intj | | - | come on! | komm schon!, los! | daj!, no daj!
evviva | intj | | - | hooray! | hurra! | hura!, živijo!
peccato | intj | | - | what a pity! (peccato che: it's a pity that) | schade! (peccato che: schade, dass) | škoda! (peccato che: škoda, da)
mi dispiace | phrase | | - | I'm sorry | es tut mir leid | žal mi je
c'era una volta | phrase | | - | once upon a time (there was) | es war einmal | nekoč je bil(a)
una volta | phrase | | - | once; one time | einmal; früher einmal | enkrat; nekoč
un giorno | phrase | | - | one day (in a story) | eines Tages | nekega dne
un po' | phrase | | - | a little, a bit | ein bisschen | malo
un quarto | phrase | | - | a quarter (e un quarto: quarter past) | ein Viertel (e un quarto: Viertel nach) | četrt (e un quarto: četrt čez)
e mezza | phrase | | - | half past (le quattro e mezza: half past four) | … Uhr dreißig (le quattro e mezza: 4:30, halb fünf) | … in trideset (le quattro e mezza: 4.30, pol petih)
che ore sono | phrase | | - | what time is it? | wie spät ist es? | koliko je ura?
per due ore | phrase | | - | for two hours | zwei Stunden lang | dve uri
piano piano | phrase | | - | slowly, little by little | ganz langsam, nach und nach | počasi, polagoma
da giovane | phrase | | - | when … was young | als … jung war | v mladosti
al posto tuo | phrase | | - | if I were you (in your place) | an deiner Stelle | na tvojem mestu
in ordine | phrase | | - | tidy, in order | aufgeräumt, in Ordnung | pospravljeno, v redu
ogni estate | phrase | | - | every summer | jeden Sommer | vsako poletje
sabato prossimo | phrase | | - | next Saturday | nächsten Samstag | prihodnjo soboto
"""
ENTRIES += r"""
voglia | noun | f | n | wish (avere voglia di: to feel like) | Lust (avere voglia di: Lust haben auf) | želja (avere voglia di: imeti voljo za)
fa | adv | | - | ago (tanto tempo fa: a long time ago) | vor (tanto tempo fa: vor langer Zeit) | pred (tanto tempo fa: pred davnimi časi)
"""
ENTRIES += r"""
palloncino | noun | m | n | balloon | Luftballon | balon
"""
ENTRIES += r"""
andarsene | verb | | x:andarsene | to go away, to leave | weggehen | oditi, iti stran
farcela | verb | | x:farcela | to manage, to make it (ce l'ho fatta!: I did it!) | es schaffen (ce l'ho fatta!: ich hab's geschafft!) | zmoči, uspeti (ce l'ho fatta!: uspelo mi je!)
"""
# pronominal verbs: their forms by hand (pronoun + ne/la + the verb), the ones the content uses kept
OWN_FORMS = {
    'andarsene': {
        'andarsene': 'infinitive', 'me ne vado': 'first-person singular present', 'te ne vai': 'second-person singular present', 'se ne va': 'third-person singular present',
        'ce ne andiamo': 'first-person plural present', 've ne andate': 'second-person plural present', 'se ne vanno': 'third-person plural present',
        'me ne andrò': 'first-person singular future', 'se ne andrà': 'third-person singular future', 'se ne andava': 'third-person singular imperfect',
        'vattene': 'second-person singular imperative', 'andiamocene': 'first-person plural imperative', 'andatevene': 'second-person plural imperative',
        "me ne sono andato": 'first-person singular present perfect', "me ne sono andata": 'first-person singular present perfect (feminine)',
        "se n'è andato": 'third-person singular present perfect', "se n'è andata": 'third-person singular present perfect (feminine)',
        'se ne sono andati': 'third-person plural present perfect', 'se ne sono andate': 'third-person plural present perfect (feminine)',
        'se ne andò': 'third-person singular historic past', 'se ne andarono': 'third-person plural historic past', 'andandosene': 'gerund',
    },
    'farcela': {
        'farcela': 'infinitive', 'ce la faccio': 'first-person singular present', 'ce la fai': 'second-person singular present', 'ce la fa': 'third-person singular present',
        'ce la facciamo': 'first-person plural present', 'ce la fate': 'second-person plural present', 'ce la fanno': 'third-person plural present',
        'ce la farò': 'first-person singular future', 'ce la farai': 'second-person singular future', 'ce la farà': 'third-person singular future',
        'ce la faremo': 'first-person plural future', 'ce la farebbe': 'third-person singular conditional',
        "ce l'ho fatta": 'first-person singular present perfect', "ce l'hai fatta": 'second-person singular present perfect', "ce l'ha fatta": 'third-person singular present perfect',
        "ce l'abbiamo fatta": 'first-person plural present perfect', "ce l'avete fatta": 'second-person plural present perfect', "ce l'hanno fatta": 'third-person plural present perfect',
        'ce la fece': 'third-person singular historic past', 'ce la fecero': 'third-person plural historic past',
    },
}
