# ONE-OFF (it-print-meanings, 2026-11-01): these scripts made the first version of the Italian course's entries in
# companion/lexicon/it.extra.json (271 entries after its first 38). That file is the source now: edit it by hand, as the
# rest of the supplement. Kept so the way the forms were made can be read and run again for new words; a run adds only
# entries the file doesn't have (it_extra_merge.py), never changes one. See docs/plans/05-built/it-print-meanings.md.
#
# Italian paradigms, written by hand (Claude): the regular conjugations by ending, the everyday irregular verbs in
# full, noun plurals and adjective endings. Each form with its grammar in the style of Wiktionary's grammar strings
# ("first-person singular present"), so the lookup reads the same either way.
PERS = ['first-person singular', 'second-person singular', 'third-person singular', 'first-person plural', 'second-person plural', 'third-person plural']
TENSES = ['present', 'imperfect', 'future', 'conditional', 'historic past', 'present subjunctive', 'imperfect subjunctive']

REG = {
    'are': {
        'present': ['o', 'i', 'a', 'iamo', 'ate', 'ano'],
        'imperfect': ['avo', 'avi', 'ava', 'avamo', 'avate', 'avano'],
        'future': ['erò', 'erai', 'erà', 'eremo', 'erete', 'eranno'],
        'conditional': ['erei', 'eresti', 'erebbe', 'eremmo', 'ereste', 'erebbero'],
        'historic past': ['ai', 'asti', 'ò', 'ammo', 'aste', 'arono'],
        'present subjunctive': ['i', 'i', 'i', 'iamo', 'iate', 'ino'],
        'imperfect subjunctive': ['assi', 'assi', 'asse', 'assimo', 'aste', 'assero'],
        'imp': ['a', 'iamo', 'ate'], 'ger': 'ando', 'pp': 'ato',
    },
    'ere': {
        'present': ['o', 'i', 'e', 'iamo', 'ete', 'ono'],
        'imperfect': ['evo', 'evi', 'eva', 'evamo', 'evate', 'evano'],
        'future': ['erò', 'erai', 'erà', 'eremo', 'erete', 'eranno'],
        'conditional': ['erei', 'eresti', 'erebbe', 'eremmo', 'ereste', 'erebbero'],
        'historic past': ['ei', 'esti', 'é', 'emmo', 'este', 'erono'],
        'present subjunctive': ['a', 'a', 'a', 'iamo', 'iate', 'ano'],
        'imperfect subjunctive': ['essi', 'essi', 'esse', 'essimo', 'este', 'essero'],
        'imp': ['i', 'iamo', 'ete'], 'ger': 'endo', 'pp': 'uto',
    },
    'ire': {
        'present': ['o', 'i', 'e', 'iamo', 'ite', 'ono'],
        'imperfect': ['ivo', 'ivi', 'iva', 'ivamo', 'ivate', 'ivano'],
        'future': ['irò', 'irai', 'irà', 'iremo', 'irete', 'iranno'],
        'conditional': ['irei', 'iresti', 'irebbe', 'iremmo', 'ireste', 'irebbero'],
        'historic past': ['ii', 'isti', 'ì', 'immo', 'iste', 'irono'],
        'present subjunctive': ['a', 'a', 'a', 'iamo', 'iate', 'ano'],
        'imperfect subjunctive': ['issi', 'issi', 'isse', 'issimo', 'iste', 'issero'],
        'imp': ['i', 'iamo', 'ite'], 'ger': 'endo', 'pp': 'ito',
    },
}


def join(stem: str, end: str, conj: str) -> str:
    """stem + ending with Italian's spelling: toccare → tocchi, cominciare → cominci, apparecchiare → apparecchi."""
    if conj == 'are' and end[:1] in ('e', 'i', 'é', 'è'):
        if stem.endswith(('c', 'g')):
            return stem + 'h' + end
        if stem.endswith(('ci', 'gi')):
            return stem[:-1] + end
        if stem.endswith('i') and end.startswith('i'):
            return stem + end[1:]
    return stem + end


def regular(inf: str, isc: bool = False) -> dict:
    conj = inf[-3:]
    stem = inf[:-3]
    e = REG[conj]
    t = {k: [join(stem, x, conj) for x in e[k]] for k in TENSES}
    imp = [join(stem, x, conj) for x in e['imp']]
    if isc:
        t['present'] = [stem + x for x in ['isco', 'isci', 'isce']] + t['present'][3:5] + [stem + 'iscono']
        t['present subjunctive'] = [stem + 'isca'] * 3 + t['present subjunctive'][3:5] + [stem + 'iscano']
        imp = [stem + 'isci'] + imp[1:]
    return {**t, 'imp': imp, 'ger': join(stem, e['ger'], conj), 'pp': join(stem, e['pp'], conj), 'inf': inf}


def irregular(key: str) -> dict:
    """The everyday irregular verbs: their own forms, the rest as the regular conjugation."""
    over = IRREGULAR[key]
    base = regular(over.get('inf', key), over.get('isc', False))
    for k, v in over.items():
        if k in ('inf', 'isc'):
            continue
        base[k] = v.split() if isinstance(v, str) and k in TENSES + ['imp'] else v
    return base


def L(s: str) -> list:
    return s.split()


IRREGULAR = {
    'essere': {'present': 'sono sei è siamo siete sono', 'imperfect': 'ero eri era eravamo eravate erano', 'future': 'sarò sarai sarà saremo sarete saranno',
               'conditional': 'sarei saresti sarebbe saremmo sareste sarebbero', 'historic past': 'fui fosti fu fummo foste furono',
               'present subjunctive': 'sia sia sia siamo siate siano', 'imperfect subjunctive': 'fossi fossi fosse fossimo foste fossero',
               'imp': 'sii siamo siate', 'ger': 'essendo', 'pp': 'stato'},
    'avere': {'present': 'ho hai ha abbiamo avete hanno', 'future': 'avrò avrai avrà avremo avrete avranno', 'conditional': 'avrei avresti avrebbe avremmo avreste avrebbero',
              'historic past': 'ebbi avesti ebbe avemmo aveste ebbero', 'present subjunctive': 'abbia abbia abbia abbiamo abbiate abbiano', 'imp': 'abbi abbiamo abbiate'},
    'andare': {'present': 'vado vai va andiamo andate vanno', 'future': 'andrò andrai andrà andremo andrete andranno', 'conditional': 'andrei andresti andrebbe andremmo andreste andrebbero',
               'present subjunctive': 'vada vada vada andiamo andiate vadano', 'imp': "va' andiamo andate", 'imp2': 'vai', 'short': "va'"},
    'fare': {'inf': 'facere', 'present': 'faccio fai fa facciamo fate fanno', 'future': 'farò farai farà faremo farete faranno', 'conditional': 'farei faresti farebbe faremmo fareste farebbero',
             'historic past': 'feci facesti fece facemmo faceste fecero', 'present subjunctive': 'faccia faccia faccia facciamo facciate facciano', 'imp': "fa' facciamo fate", 'imp2': 'fai',
             'pp': 'fatto', 'infinitive': 'fare', 'short': "fa'"},
    'dare': {'present': 'do dai dà diamo date danno', 'future': 'darò darai darà daremo darete daranno', 'conditional': 'darei daresti darebbe daremmo dareste darebbero',
             'historic past': 'diedi desti diede demmo deste diedero', 'present subjunctive': 'dia dia dia diamo diate diano', 'imperfect subjunctive': 'dessi dessi desse dessimo deste dessero',
             'imp': "da' diamo date", 'imp2': 'dai', 'short': "da'", 'more': {'detti': 'first-person singular historic past', 'dette': 'third-person singular historic past', 'dettero': 'third-person plural historic past'}},
    'dire': {'inf': 'dicere', 'present': 'dico dici dice diciamo dite dicono', 'future': 'dirò dirai dirà diremo direte diranno', 'conditional': 'direi diresti direbbe diremmo direste direbbero',
             'historic past': 'dissi dicesti disse dicemmo diceste dissero', 'imp': "di' diciamo dite", 'pp': 'detto', 'infinitive': 'dire', 'short': "di'"},
    'stare': {'historic past': 'stetti stesti stette stemmo steste stettero', 'present': 'sto stai sta stiamo state stanno', 'future': 'starò starai starà staremo starete staranno',
              'conditional': 'starei staresti starebbe staremmo stareste starebbero', 'present subjunctive': 'stia stia stia stiamo stiate stiano',
              'imperfect subjunctive': 'stessi stessi stesse stessimo steste stessero', 'imp': "sta' stiamo state", 'imp2': 'stai', 'short': "sta'"},
    'venire': {'present': 'vengo vieni viene veniamo venite vengono', 'future': 'verrò verrai verrà verremo verrete verranno', 'conditional': 'verrei verresti verrebbe verremmo verreste verrebbero',
               'historic past': 'venni venisti venne venimmo veniste vennero', 'present subjunctive': 'venga venga venga veniamo veniate vengano', 'imp': 'vieni veniamo venite', 'pp': 'venuto'},
    'volere': {'present': 'voglio vuoi vuole vogliamo volete vogliono', 'future': 'vorrò vorrai vorrà vorremo vorrete vorranno', 'conditional': 'vorrei vorresti vorrebbe vorremmo vorreste vorrebbero',
               'historic past': 'volli volesti volle volemmo voleste vollero', 'present subjunctive': 'voglia voglia voglia vogliamo vogliate vogliano', 'imp': ''},
    'potere': {'present': 'posso puoi può possiamo potete possono', 'future': 'potrò potrai potrà potremo potrete potranno', 'conditional': 'potrei potresti potrebbe potremmo potreste potrebbero',
               'historic past': 'potei potesti poté potemmo poteste poterono', 'present subjunctive': 'possa possa possa possiamo possiate possano', 'imp': ''},
    'dovere': {'present': 'devo devi deve dobbiamo dovete devono', 'future': 'dovrò dovrai dovrà dovremo dovrete dovranno', 'conditional': 'dovrei dovresti dovrebbe dovremmo dovreste dovrebbero',
               'historic past': 'dovetti dovesti dovette dovemmo doveste dovettero', 'present subjunctive': 'debba debba debba dobbiamo dobbiate debbano', 'imp': ''},
    'sapere': {'present': 'so sai sa sappiamo sapete sanno', 'future': 'saprò saprai saprà sapremo saprete sapranno', 'conditional': 'saprei sapresti saprebbe sapremmo sapreste saprebbero',
               'historic past': 'seppi sapesti seppe sapemmo sapeste seppero', 'present subjunctive': 'sappia sappia sappia sappiamo sappiate sappiano', 'imp': 'sappi sappiamo sappiate'},
    'tenere': {'present': 'tengo tieni tiene teniamo tenete tengono', 'future': 'terrò terrai terrà terremo terrete terranno', 'conditional': 'terrei terresti terrebbe terremmo terreste terrebbero',
               'historic past': 'tenni tenesti tenne tenemmo teneste tennero', 'present subjunctive': 'tenga tenga tenga teniamo teniate tengano', 'imp': 'tieni teniamo tenete'},
    'sedere': {'present': 'siedo siedi siede sediamo sedete siedono', 'historic past': 'sedetti sedesti sedette sedemmo sedeste sedettero',
               'present subjunctive': 'sieda sieda sieda sediamo sediate siedano', 'imp': 'siedi sediamo sedete'},
    'cuocere': {'present': 'cuocio cuoci cuoce cuociamo cuocete cuociono', 'historic past': 'cossi cuocesti cosse cuocemmo cuoceste cossero',
                'present subjunctive': 'cuocia cuocia cuocia cuociamo cuociate cuociano', 'imp': 'cuoci cuociamo cuocete', 'pp': 'cotto'},
    'mettere': {'historic past': 'misi mettesti mise mettemmo metteste misero', 'pp': 'messo'},
    'prendere': {'historic past': 'presi prendesti prese prendemmo prendeste presero', 'pp': 'preso'},
    'chiedere': {'historic past': 'chiesi chiedesti chiese chiedemmo chiedeste chiesero', 'pp': 'chiesto'},
    'mordere': {'historic past': 'morsi mordesti morse mordemmo mordeste morsero', 'pp': 'morso'},
    'vivere': {'historic past': 'vissi vivesti visse vivemmo viveste vissero', 'future': 'vivrò vivrai vivrà vivremo vivrete vivranno',
               'conditional': 'vivrei vivresti vivrebbe vivremmo vivreste vivrebbero', 'pp': 'vissuto'},
    'conoscere': {'historic past': 'conobbi conoscesti conobbe conoscemmo conosceste conobbero', 'pp': 'conosciuto'},
    'piovere': {'historic past': 'piovvi piovesti piovve piovemmo pioveste piovvero', 'pp': 'piovuto'},
    'scoprire': {'pp': 'scoperto'},
    'aprire': {'pp': 'aperto'},
}

# the clitics a verb's infinitive, imperative and gerund join (guardami, toccarlo, dammelo, tenendolo), with what each says
CLITICS = {
    'mi': 'me', 'ti': 'you', 'lo': 'it, him', 'la': 'it, her', 'li': 'them', 'le': 'them; to her', 'ci': 'us; there', 'vi': 'you all',
    'gli': 'to him, to them', 'ne': 'of it', 'si': 'oneself',
    'melo': 'it to me', 'mela': 'it to me', 'meli': 'them to me', 'mele': 'them to me', 'mene': 'of it to me',
    'telo': 'it to you', 'tela': 'it to you', 'teli': 'them to you', 'tele': 'them to you', 'tene': 'of it to you',
    'glielo': 'it to him, to her', 'gliela': 'it to him, to her', 'glieli': 'them to him, to her', 'gliele': 'them to him, to her', 'gliene': 'of it to him, to her',
    'celo': 'it to us', 'cene': 'of it to us', 'velo': 'it to you all', 'vela': 'it to you all',
}
SHORT_JOINS = {
    "va'": [('vacci', 'ci'), ('vattene', 'tene'), ('vanne', 'ne')],
    "da'": [('dammi', 'mi'), ('dacci', 'ci'), ('danne', 'ne'), ('dammelo', 'melo'), ('dammela', 'mela'), ('dammeli', 'meli'), ('dammele', 'mele'), ('dammene', 'mene'),
            ('daglielo', 'glielo'), ('dagliela', 'gliela'), ('daglieli', 'glieli'), ('dagliele', 'gliele'), ('dagliene', 'gliene')],
    "di'": [('dimmi', 'mi'), ('dillo', 'lo'), ('dille', 'le'), ('digli', 'gli'), ('dicci', 'ci'), ('dimmelo', 'melo'), ('diglielo', 'glielo')],
    "fa'": [('fammi', 'mi'), ('facci', 'ci'), ('fagli', 'gli'), ('fammelo', 'melo'), ('fammela', 'mela'), ('faglielo', 'glielo')],
    "sta'": [('stammi', 'mi')],
}
REFLEXIVE = {'mi': 'myself', 'ti': 'yourself', 'si': 'himself, herself', 'ci': 'ourselves', 'vi': 'yourselves'}
SHOW = {'melo': 'me lo', 'mela': 'me la', 'meli': 'me li', 'mele': 'me le', 'mene': 'me ne', 'telo': 'te lo', 'tela': 'te la', 'teli': 'te li', 'tele': 'te le',
        'tene': 'te ne', 'celo': 'ce lo', 'cela': 'ce la', 'cene': 'ce ne', 'velo': 've lo', 'vela': 've la', 'sene': 'se ne'}


def label_persons(slots: list) -> str:
    """'first-person singular present; first/second/third-person singular present subjunctive': a form's slots, the
    persons of one number and tense together."""
    groups = {}
    order = []
    for person, number, tense in slots:
        k = (number, tense)
        if k not in groups:
            groups[k] = []
            order.append(k)
        if person not in groups[k]:
            groups[k].append(person)
    out = []
    for number, tense in order:
        ps = groups[(number, tense)]
        out.append(f"{'/'.join(ps)}-person {number} {tense}".strip())
    return '; '.join(out)


def verb_forms(p: dict, reflexive: bool) -> dict:
    """form → grammar for a verb's paradigm, its clitic forms too."""
    slots: dict = {}
    other: dict = {}
    def put(f, person, number, tense):
        slots.setdefault(f, []).append((person, number, tense))
    for tense in TENSES:
        for i, f in enumerate(p[tense]):
            put(f, ['first', 'second', 'third'][i % 3], 'singular' if i < 3 else 'plural', tense)
    imp = [x for x in p['imp'] if x] if isinstance(p['imp'], list) else []
    if len(imp) == 3:
        put(imp[0], 'second', 'singular', 'imperative')
        put(imp[1], 'first', 'plural', 'imperative')
        put(imp[2], 'second', 'plural', 'imperative')
    if p.get('imp2'):
        put(p['imp2'], 'second', 'singular', 'imperative')
    out = {f: label_persons(s) for f, s in slots.items()}
    if reflexive:
        # a reflexive verb's finite forms are the plain verb's ("gira il frico", "girino" a tadpole): only with the
        # pronoun before them ("si gira", "mi sono lavata"), as a line's words around the tapped one find them
        pron = {'first-person singular': 'mi', 'second-person singular': 'ti', 'third-person singular': 'si', 'first-person plural': 'ci', 'second-person plural': 'vi', 'third-person plural': 'si'}
        aux = {'mi': 'sono', 'ti': 'sei', 'si': 'è', 'ci': 'siamo', 'vi': 'siete'}
        joined = {}
        for f, ss in slots.items():
            for person, number, tense in ss:
                if tense == 'imperative':
                    continue
                pr = pron[f'{person}-person {number}']
                joined.setdefault(f'{pr} {f}', []).append((person, number, tense))
        out = {f: label_persons(s) for f, s in joined.items()}
        pp0 = p['pp'][:-1]
        who = {'mi': 'first-person singular', 'ti': 'second-person singular', 'si': 'third-person singular', 'ci': 'first-person plural', 'vi': 'second-person plural'}
        for pr, a in aux.items():
            for end, g in [('o', ''), ('a', ' (feminine)')] if pr in ('mi', 'ti', 'si') else [('i', ''), ('e', ' (feminine)')]:
                out[f'{pr} {a} {pp0}{end}'] = f'{who[pr]} present perfect{g}'
        for end, g in [('i', ''), ('e', ' (feminine)')]:
            out[f'si sono {pp0}{end}'] = f'third-person plural present perfect{g}'
    inf = p.get('infinitive', p['inf'])
    pp = p['pp']
    if reflexive:
        slots.pop(inf, None)
    ppstem = pp[:-1]
    for f, g in [] if reflexive else [(inf, 'infinitive'), (p['ger'], 'gerund'), (pp, 'past participle'), (ppstem + 'a', 'feminine singular past participle'),
                 (ppstem + 'i', 'masculine plural past participle'), (ppstem + 'e', 'feminine plural past participle')]:
        out[f] = f"{out[f]}; {g}" if f in out and g not in out[f] else g
    for f, g in p.get('more', {}).items():
        out[f] = g
    # the clitics joined: the infinitive without its last e, the imperative, the gerund
    short = p.get('short')
    joins = [(inf[:-1], 'infinitive', False), (p['ger'], 'gerund', False)]
    if len(imp) == 3:
        joins += [*([] if short else [(imp[0], 'second-person singular imperative', False)]), (imp[1], 'first-person plural imperative', False), (imp[2], 'second-person plural imperative', False)]
    # da', di', fa', sta', va' double the clitic's consonant (dammi, dimmi, vattene): only the joins that are no other
    # word (dalla, dagli are da + the article; stalla a stable, valle a valley; fallo a foul)
    for form, c in SHORT_JOINS.get(short or '', []):
        out.setdefault(form, f"second-person singular imperative, with {SHOW.get(c, c)} ({CLITICS[c]})")
    if p.get('imp2') and short:
        pass  # dai + lo isn't joined: da' + lo → dallo
    for stem, g, double in joins:
        for c, said in CLITICS.items():
            if reflexive and c not in REFLEXIVE:
                continue
            joined = c
            if double and not c.startswith('gli'):
                joined = c[0] + c
            # a first-person plural imperative with ci/ne doubles nothing; tenendolo, guardami as written
            form = stem + joined
            what = REFLEXIVE.get(c) if reflexive and c in REFLEXIVE else said
            out.setdefault(form, f"{g}, with {SHOW.get(c, c)} ({what})")
    return out


def noun_forms(lemma: str, spec: str) -> dict:
    if spec == '-':
        return {lemma: 'singular'}
    if spec == 'n=':
        return {lemma: 'singular; plural'}
    if spec.startswith('n:'):
        out = {lemma: 'singular'}
        for pl in spec[2:].split('/'):
            # signor@singular (before a name): another form, its grammar given
            form, _, g = pl.partition('@')
            out[form] = g or 'plural'
        return out
    if lemma.endswith('o'):
        pl = lemma[:-1] + 'i'
    elif lemma.endswith('a'):
        pl = lemma[:-1] + 'e'
    elif lemma.endswith('e'):
        pl = lemma[:-1] + 'i'
    else:
        pl = lemma
    return {lemma: 'singular', pl: 'plural'}


SPECIAL_ADJ = {
    'bello': {'bello': 'masculine singular', 'bella': 'feminine singular', 'belli': 'masculine plural', 'belle': 'feminine plural', 'bel': 'masculine singular (before a consonant)',
              'bei': 'masculine plural (before a consonant)', 'begli': 'masculine plural (before a vowel, s + consonant, z)'},
    'mio': {'mio': 'masculine singular', 'mia': 'feminine singular', 'miei': 'masculine plural', 'mie': 'feminine plural'},
    'tuo': {'tuo': 'masculine singular', 'tua': 'feminine singular', 'tuoi': 'masculine plural', 'tue': 'feminine plural'},
    'nessuno': {'nessuno': 'masculine singular', 'nessuna': 'feminine singular', 'nessun': 'masculine singular (before a noun)'},
    'qualcuno': {'qualcuno': 'masculine singular', 'qualcuna': 'feminine singular'},
}


def adj_forms(lemma: str, spec: str) -> dict:
    if spec.startswith('a:'):
        return dict(SPECIAL_ADJ[spec[2:]])
    if spec.startswith('a2'):
        out = {lemma: 'singular', lemma[:-1] + 'i': 'plural'}
        if ':' in spec:
            out[spec.split(':')[1]] = 'singular (before a noun)'
        return out
    s = lemma[:-1]
    return {lemma: 'masculine singular', s + 'a': 'feminine singular', s + 'i': 'masculine plural', s + 'e': 'feminine plural'}
