// Site translations — ported from the app's strings.xml files.
// Keys used by both index.html and docs.html.
// The app already carries verified human translations for all 8 locales,
// so this file contains zero machine-translated strings.

const LOCALES = {
  en: {
    label: 'English', flag: 'EN',
    // hero
    hero_chip:    'No cloud · No subscription · No Wi-Fi',
    hero_h1_a:   'Your garage opens',
    hero_h1_b:   'before you stop the car',
    hero_sub:    'One tap on your Android Auto screen — or say',
    hero_sub_em: '"Open the garage"',
    hero_sub2:   '— and the ESP32 triggers your fob. Or just drive up: the geofence opens it before you stop. No internet. No cloud.',
    hero_cta_play:   'Get on Google Play',
    hero_cta_github: 'GitHub',
    // ticker
    ticker: ['Android Auto','ESP32 BLE','No internet required','HMAC-SHA256 auth','Open source','Voice commands','Geofence auto-open','No soldering option','Quick Settings tile','Deep sleep firmware'],
    // who section
    who_chip:   'The situation',
    who_h2_a:  'Shared garage.',
    who_h2_b:  'Borrowed fob.',
    who_p1:    "You use a communal garage — an apartment block, a rented space — where you can't touch the motor or the gate controller. All you have is a key fob.",
    who_p2:    "This gives you a hands-free way to open the door from your car's Android Auto screen. No internet. No cloud. No hub. The ESP32 presses the fob button for you.",
    stat_ble:     '< 1s',   stat_ble_l:    'BLE round-trip typical',
    stat_lang:    '8',      stat_lang_l:   'Languages supported',
    stat_cloud:   '0',      stat_cloud_l:  'Cloud dependencies',
    stat_trigger: '3',      stat_trigger_l:'Trigger options (incl. no-solder)',
    // features
    feat_chip: 'Built right',
    feat_h2_a: 'Everything you\'d expect.',
    feat_h2_b: 'Nothing you don\'t.',
    feat1_h: 'HMAC-SHA256 Auth',    feat1_p: 'Your password never travels over the air — only a hash of a fresh random nonce. Replay attacks are impossible by design.',
    feat2_h: 'Android Auto native', feat2_p: 'Built on the Car App Library. A single button on the car screen — safe to glance at from the driver\'s seat, try-again on failure.',
    feat3_h: 'Voice commands',      feat3_p: 'Say "Open the garage" through the car mic or steering wheel button — Google routes the intent straight to the BLE command. All 8 languages.',
    feat4_h: 'Geofence auto-open',    feat4_p: 'Set a radius on the map in Settings. When you enter the zone with Android Auto connected, the gate opens automatically — no tap, no phone interaction. A notification confirms every auto-fire.',
    feat5_h: 'Live presence indicator', feat5_p: 'A low-power background BLE scan shows a green dot when the opener is in range — on both the phone screen and the car display.',
    feat6_h: 'Renter-friendly option',  feat6_p: "Can't modify the fob? Option B uses a relay to switch the fob's battery power — copper tape discs in the battery slot, no soldering inside. Fully reversible when you move out.",
    // how it works
    how_chip: 'The flow',
    how_h2_a: 'From phone to gate',
    how_h2_b: 'in under a second',
    step1_h: 'You tap the car screen (or say the word)',         step1_p: 'One button on your Android Auto display. Or just ask Google — "Open the garage" works in 8 languages.',
    step2_h: 'Phone sends an HMAC command over BLE',             step2_p: 'The app hashes a random nonce with your password and sends only the hash — your password never leaves the phone.',
    step3_h: 'ESP32 verifies and fires',                         step3_p: 'The ESP32 at your garage validates the HMAC, triggers the relay, and sends a status notification back.',
    step4_h: 'Screen confirms — gate opens',                     step4_p: 'The ring fills green on success, shows "Try Again" on failure so you never have to scramble while driving. Typical round-trip: under 1 second.',
    // hardware
    hw_chip: 'Pick your setup',
    hw_h2_a: 'Three trigger options.',
    hw_h2_b: 'One firmware.',
    hw_sub:  'The ESP32 firmware handles all three — pick the one that fits your situation.',
    hw_a_h: 'Relay module', hw_a_badge: 'Recommended',
    hw_a_p: 'A small relay module shorts the fob\'s button pads when triggered. Beginner-friendly — the module\'s onboard driver handles everything. Use a 3V coil (e.g. Songle SRD-03VDC-SL-C) directly on the ESP32\'s 3.3V rail.',
    hw_a_t1: '~$1–2', hw_a_t2: 'Soldering required',
    hw_b_h: 'Relay + power switching', hw_b_badge: 'Renter-friendly',
    hw_b_p: 'Same relay module as A, but switches the fob\'s battery ground rail instead of the button pads. Copper tape discs slide into the battery slot — no soldering inside the fob. Fully reversible.',
    hw_b_t1: 'No fob modification', hw_b_t2: '~$1–2',
    hw_c_h: 'Transistor',
    hw_c_p: 'A single NPN transistor shorts the fob\'s button pads. Lowest power draw (~20 mA vs ~70 mA for relay). Requires transistor biasing knowledge. Best if power budget is critical.',
    hw_c_t1: '~$0.10', hw_c_t2: 'Advanced',
    // deploy
    deploy_chip: 'Deployment',
    deploy_h2_a: 'In the car or at',
    deploy_h2_b: 'the garage entrance',
    deploy_car_h: 'In the car', deploy_car_badge: 'Best for solo',
    deploy_car_p: 'ESP32 and fob both ride in the car, powered off the USB socket. BLE always works because everything is in the same vehicle. Zero range concerns.',
    deploy_car_li1: 'Auto-fires when you pull up to the gate',
    deploy_car_li2: 'Charges whenever the car is on',
    deploy_car_li3: 'Nothing to install, nothing to maintain at the garage',
    deploy_ga_h: 'At the garage', deploy_ga_badge: 'Multi-user',
    deploy_ga_p: 'ESP32 lives near the gate, powered by wall outlet, power bank, or solar. Anyone you share the password with can open the same gate from their own phone.',
    deploy_ga_li1: 'Supports multiple users and vehicles',
    deploy_ga_li2: 'Deep sleep between polls (~12 months on a power bank)',
    deploy_ga_li3: 'BLE range 10–30 m through walls — plan placement',
    // security
    sec_h2: 'Your password',
    sec_h2_b: 'never leaves the phone',
    sec_p: 'Each connection generates a fresh random nonce on the ESP32. The phone hashes the nonce with your password using HMAC-SHA256 and sends only the hash. The ESP32 verifies it locally. A captured packet is worthless — the nonce is used once and discarded.',
    sec_link: 'Read the security model',
    // cta
    cta_h2: 'Ready to open hands-free?',
    cta_p:  'Get the Android app, download the flash tool, and you\'re up in one step.',
    cta_play: 'Get on Google Play',
    cta_docs: 'Read the docs',
    // docs nav labels (used on docs.html nav)
    nav_back: '← Back to home',
    // nav labels
    nav_features: 'Features',
    nav_how:      'How it works',
    nav_hardware: 'Hardware',
    nav_docs:     'Docs',
    // docs page strings
    docs_title: 'Setup guide & reference',
    docs_sub:   'Everything from flashing the ESP32 to driving away with a gate that opens itself.',
    docs_qs_h:  'Quick start',
    docs_qs_p:  'Three steps from unboxing to working garage opener.',
    docs_step1_h: 'Flash the firmware',
    docs_step1_p: 'Download GarageAAtoESP32-flash-tool.zip from the Releases page, extract it, and double-click flash.bat. The tool installs PlatformIO if needed, lets you pick your board, detects the COM port, asks for your password, compiles, and flashes.',
    docs_step2_h: 'Install the Android app',
    docs_step2_p: 'Get it from the Play Store, or download the APK from the Releases page and sideload it.',
    docs_step3_h: 'Pair and go',
    docs_step3_p: 'Open Garage Opener on your phone — it walks you through scanning for your ESP32 and entering your password. Then connect your phone to Android Auto and tap Open Garage.',
    docs_stuck_h: 'Still stuck?',
    docs_stuck_p: 'Open an issue on GitHub and include your serial monitor output and board type.',
    docs_issue: 'Open an issue',
  },

  es: {
    label: 'Español', flag: 'ES',
    hero_chip:    'Sin nube · Sin suscripción · Sin Wi-Fi',
    hero_h1_a:   'Tu garaje se abre',
    hero_h1_b:   'antes de que pares el coche',
    hero_sub:    'Un toque en tu pantalla de Android Auto — o di',
    hero_sub_em: '"Abre el garaje"',
    hero_sub2:   '— y el ESP32 pulsa el mando por ti. O simplemente acércate: la geovalla lo abre antes de que pares. Sin internet. Sin nube.',
    hero_cta_play:   'Descargar en Google Play',
    hero_cta_github: 'GitHub',
    ticker: ['Android Auto','ESP32 BLE','Sin internet','Autenticación HMAC-SHA256','Código abierto','Comandos de voz','Apertura por geovalla','Sin soldadura','Acceso rápido','Bajo consumo'],
    who_chip: 'La situación',
    who_h2_a: 'Garaje compartido.',
    who_h2_b: 'Mando prestado.',
    who_p1: 'Usas un garaje comunitario — un bloque de pisos, un espacio alquilado — donde no puedes tocar el motor ni el controlador de la puerta. Solo tienes un mando.',
    who_p2: 'Esto te da una forma manos libres de abrir la puerta desde la pantalla de Android Auto de tu coche. Sin internet. Sin nube. Sin hub. El ESP32 pulsa el botón del mando por ti.',
    stat_ble: '< 1s', stat_ble_l: 'ida y vuelta BLE típica',
    stat_lang: '8', stat_lang_l: 'Idiomas soportados',
    stat_cloud: '0', stat_cloud_l: 'Dependencias en la nube',
    stat_trigger: '3', stat_trigger_l: 'Opciones de activación (incl. sin soldadura)',
    feat_chip: 'Bien hecho',
    feat_h2_a: 'Todo lo que esperarías.',
    feat_h2_b: 'Nada que no necesites.',
    feat1_h: 'Autenticación HMAC-SHA256', feat1_p: 'Tu contraseña nunca viaja por el aire — solo el hash de un nonce aleatorio. Los ataques de repetición son imposibles por diseño.',
    feat2_h: 'Android Auto nativo',       feat2_p: 'Construido con la Car App Library. Un solo botón en la pantalla del coche, seguro de ver desde el asiento del conductor.',
    feat3_h: 'Comandos de voz',           feat3_p: 'Di "Abre el garaje" por el micrófono del coche — Google envía la orden directamente al comando BLE. En los 8 idiomas.',
    feat4_h: 'Apertura automática por geovalla', feat4_p: 'Define un radio en el mapa desde Ajustes. Al entrar en la zona con Android Auto conectado, la puerta se abre sola — sin tocar nada. Una notificación confirma cada disparo automático.',
    feat5_h: 'Indicador de presencia',    feat5_p: 'Un escáner BLE de bajo consumo muestra un punto verde cuando el abridor está al alcance — tanto en el teléfono como en el coche.',
    feat6_h: 'Opción para inquilinos',    feat6_p: '¿No puedes modificar el mando? La opción B usa un relé para conmutar la alimentación de la pila — discos de cinta de cobre en el compartimento, sin soldar dentro. Totalmente reversible.',
    how_chip: 'El flujo',
    how_h2_a: 'Del móvil a la puerta',
    how_h2_b: 'en menos de un segundo',
    step1_h: 'Tocas la pantalla del coche (o lo dices)',         step1_p: 'Un botón en tu pantalla de Android Auto. O simplemente pregúntale a Google — "Abre el garaje" funciona en 8 idiomas.',
    step2_h: 'El móvil envía un comando HMAC por BLE',           step2_p: 'La app aplica hash a un nonce aleatorio con tu contraseña y envía solo el hash — la contraseña no sale del móvil.',
    step3_h: 'El ESP32 verifica y actúa',                        step3_p: 'El ESP32 en tu garaje valida el HMAC, activa el relé y devuelve una notificación de estado.',
    step4_h: 'La pantalla confirma — la puerta se abre',         step4_p: 'El anillo se pone verde al éxito, muestra "Reintentar" al fallo. Tiempo de ida y vuelta típico: menos de 1 segundo.',
    hw_chip: 'Elige tu configuración',
    hw_h2_a: 'Tres opciones de activación.',
    hw_h2_b: 'Un firmware.',
    hw_sub: 'El firmware del ESP32 gestiona las tres — elige la que se adapte a tu situación.',
    hw_a_h: 'Módulo relé', hw_a_badge: 'Recomendado',
    hw_a_p: 'Un pequeño módulo relé cortocircuita los pads del botón del mando. Fácil para principiantes — el driver integrado lo gestiona todo. Usa una bobina de 3V (p.ej. Songle SRD-03VDC-SL-C) directamente en el rail 3.3V del ESP32.',
    hw_a_t1: '~$1–2', hw_a_t2: 'Soldadura requerida',
    hw_b_h: 'Relé + conmutación de potencia', hw_b_badge: 'Apto para inquilinos',
    hw_b_p: 'El mismo módulo relé que A, pero conmuta el rail de batería del mando en lugar de los pads del botón. Discos de cinta de cobre en el hueco de la pila — sin soldar dentro del mando. Totalmente reversible.',
    hw_b_t1: 'Sin modificar el mando', hw_b_t2: '~$1–2',
    hw_c_h: 'Pulso capacitivo', hw_c_badge: 'Sin soldadura',
    hw_c_p: 'El ESP32 envía un pulso de carga a un trozo de cinta de cobre sobre el botón capacitivo de un Fingerbot Plus — que luego pulsa el mando mecánicamente. Nada se abre ni se suelda.',
    hw_c_t1: 'Para inquilinos', hw_c_t2: '+0,5–1s de latencia',
    deploy_chip: 'Despliegue',
    deploy_h2_a: 'En el coche o en',
    deploy_h2_b: 'la entrada del garaje',
    deploy_car_h: 'En el coche', deploy_car_badge: 'Mejor para uno solo',
    deploy_car_p: 'El ESP32 y el mando van en el coche, alimentados por el USB. El BLE siempre funciona porque todo está en el mismo vehículo.',
    deploy_car_li1: 'Se activa automáticamente al llegar a la puerta',
    deploy_car_li2: 'Se carga cuando el coche está encendido',
    deploy_car_li3: 'No hay nada que instalar ni mantener en el garaje',
    deploy_ga_h: 'En el garaje', deploy_ga_badge: 'Multiusuario',
    deploy_ga_p: 'El ESP32 vive cerca de la puerta, alimentado por enchufe, batería o solar. Cualquiera con quien compartas la contraseña puede abrir la puerta desde su móvil.',
    deploy_ga_li1: 'Soporta varios usuarios y vehículos',
    deploy_ga_li2: 'Deep sleep entre encuestas (~12 meses con batería)',
    deploy_ga_li3: 'Rango BLE 10–30 m a través de paredes — planifica la ubicación',
    sec_h2: 'Tu contraseña', sec_h2_b: 'nunca sale del móvil',
    sec_p: 'Cada conexión genera un nonce aleatorio nuevo en el ESP32. El móvil aplica HMAC-SHA256 al nonce con tu contraseña y envía solo el hash. El ESP32 lo verifica localmente. Un paquete capturado no sirve de nada.',
    sec_link: 'Leer el modelo de seguridad',
    cta_h2: '¿Listo para abrir con las manos libres?',
    cta_p: 'Descarga la app Android, el flash tool y en un paso ya funciona.',
    cta_play: 'Descargar en Google Play',
    cta_docs: 'Leer la documentación',
    nav_back: '← Volver al inicio',
    nav_features: 'Funcionalidades',
    nav_how:      'Cómo funciona',
    nav_hardware: 'Hardware',
    nav_docs:     'Documentación',
    docs_title: 'Guía de configuración y referencia',
    docs_sub: 'Todo lo necesario para flashear el ESP32 y salir conduciendo con la puerta que se abre sola.',
    docs_qs_h: 'Inicio rápido',
    docs_qs_p: 'Tres pasos desde el unboxing hasta un abridor funcionando.',
    docs_step1_h: 'Flashear el firmware', docs_step1_p: 'Descarga GarageAAtoESP32-flash-tool.zip desde la página de Releases, descomprímelo y haz doble clic en flash.bat.',
    docs_step2_h: 'Instalar la app Android', docs_step2_p: 'Descárgala de Google Play o instala el APK desde la página de Releases.',
    docs_step3_h: 'Vincular y listo', docs_step3_p: 'Abre Garage Opener en tu móvil — te guiará para escanear el ESP32 e introducir tu contraseña. Luego conecta tu móvil a Android Auto y pulsa Abrir garaje.',
    docs_stuck_h: '¿Sigues atascado?', docs_stuck_p: 'Abre un issue en GitHub e incluye la salida del monitor serie y el tipo de placa.',
    docs_issue: 'Abrir un issue',
  },

  fr: {
    label: 'Français', flag: 'FR',
    hero_chip:    'Sans cloud · Sans abonnement · Sans Wi-Fi',
    hero_h1_a:   'Votre garage s\'ouvre',
    hero_h1_b:   'avant même d\'arrêter le moteur',
    hero_sub:    'Un seul geste sur votre écran Android Auto — ou dites',
    hero_sub_em: '"Ouvrir le garage"',
    hero_sub2:   '— et l\'ESP32 appuie sur la télécommande. Ou approchez simplement : la géofence l\'ouvre avant même que vous vous arrêtiez. Sans internet. Sans cloud.',
    hero_cta_play:   'Télécharger sur Google Play',
    hero_cta_github: 'GitHub',
    ticker: ['Android Auto','ESP32 BLE','Sans internet','Auth HMAC-SHA256','Open source','Commandes vocales','Ouverture par géofence','Sans soudure','Tuile accès rapide','Firmware basse conso'],
    who_chip: 'La situation',
    who_h2_a: 'Garage partagé.',
    who_h2_b: 'Télécommande empruntée.',
    who_p1: "Vous utilisez un garage commun — une résidence, un espace loué — sans pouvoir toucher au moteur ou au contrôleur de porte. Vous n'avez qu'une télécommande.",
    who_p2: "Cela vous donne un moyen mains libres d'ouvrir la porte depuis l'écran Android Auto de votre voiture. Sans internet. Sans cloud. Sans hub. L'ESP32 appuie sur le bouton de la télécommande à votre place.",
    stat_ble: '< 1s', stat_ble_l: 'aller-retour BLE typique',
    stat_lang: '8', stat_lang_l: 'Langues supportées',
    stat_cloud: '0', stat_cloud_l: 'Dépendances cloud',
    stat_trigger: '3', stat_trigger_l: 'Options de déclenchement (dont sans soudure)',
    feat_chip: 'Bien conçu',
    feat_h2_a: 'Tout ce que vous attendez.',
    feat_h2_b: 'Rien de superflu.',
    feat1_h: 'Auth HMAC-SHA256',       feat1_p: "Votre mot de passe ne transite jamais par les airs — uniquement le hash d'un nonce aléatoire. Les attaques par rejeu sont impossibles par conception.",
    feat2_h: 'Android Auto natif',     feat2_p: "Construit sur la Car App Library. Un seul bouton sur l'écran de la voiture — sûr à regarder depuis le siège conducteur.",
    feat3_h: 'Commandes vocales',      feat3_p: 'Dites "Ouvrir le garage" dans le micro de la voiture — Google achemine l\'intention directement vers la commande BLE. Dans les 8 langues.',
    feat4_h: 'Ouverture automatique par géofence', feat4_p: "Définissez un rayon sur la carte dans les paramètres. Quand vous entrez dans la zone avec Android Auto connecté, le portail s'ouvre tout seul — sans toucher l'écran. Une notification confirme chaque déclenchement automatique.",
    feat5_h: 'Indicateur de présence', feat5_p: "Un scan BLE en arrière-plan affiche un point vert quand l'ouvre-porte est à portée — sur le téléphone et sur l'écran auto.",
    feat6_h: 'Option locataire',       feat6_p: "Impossible de modifier la télécommande ? L'option B utilise un relais pour commuter l'alimentation — disques de ruban de cuivre dans le logement de pile, sans soudure. Entièrement réversible.",
    how_chip: 'Le flux',
    how_h2_a: 'Du téléphone au portail',
    how_h2_b: 'en moins d\'une seconde',
    step1_h: 'Vous touchez l\'écran (ou le dites)', step1_p: "Un bouton sur votre écran Android Auto. Ou demandez à Google — \"Ouvrir le garage\" fonctionne dans 8 langues.",
    step2_h: 'Le téléphone envoie une commande HMAC via BLE', step2_p: "L'app hache un nonce aléatoire avec votre mot de passe et n'envoie que le hash — le mot de passe ne quitte jamais le téléphone.",
    step3_h: "L'ESP32 vérifie et agit", step3_p: "L'ESP32 dans votre garage valide le HMAC, déclenche le relais et renvoie une notification de statut.",
    step4_h: 'L\'écran confirme — le portail s\'ouvre', step4_p: 'L\'anneau passe au vert en cas de succès, affiche "Réessayer" en cas d\'échec. Aller-retour typique : moins d\'une seconde.',
    hw_chip: 'Choisissez votre configuration',
    hw_h2_a: 'Trois options de déclenchement.',
    hw_h2_b: 'Un seul firmware.',
    hw_sub: 'Le firmware ESP32 gère les trois — choisissez celle qui correspond à votre situation.',
    hw_a_h: 'Module relais', hw_a_badge: 'Recommandé',
    hw_a_p: 'Un petit module relais court-circuite les pads du bouton de la télécommande. Accessible aux débutants — le driver intégré gère tout. Utilisez une bobine 3V (ex. Songle SRD-03VDC-SL-C) directement sur le rail 3,3V de l\'ESP32.',
    hw_a_t1: '~1 $', hw_a_t2: 'Soudure requise',
    hw_b_h: 'Relais + commutation d\'alimentation', hw_b_badge: 'Locataire-friendly',
    hw_b_p: 'Même module relais que A, mais commute le rail de batterie de la télécommande au lieu des pads du bouton. Disques de ruban de cuivre dans le logement de pile — pas de soudure à l\'intérieur. Entièrement réversible.',
    hw_b_t1: 'Sans modification', hw_b_t2: '~1 $',
    hw_c_h: 'Impulsion capacitive', hw_c_badge: 'Sans soudure',
    hw_c_p: "L'ESP32 envoie une impulsion de charge vers un morceau de ruban de cuivre sur le bouton capacitif d'un Fingerbot Plus — qui appuie mécaniquement sur la télécommande. Rien n'est ouvert ni soudé.",
    hw_c_t1: 'Pour les locataires', hw_c_t2: '+0,5–1s de latence',
    deploy_chip: 'Déploiement',
    deploy_h2_a: 'Dans la voiture ou à',
    deploy_h2_b: "l'entrée du garage",
    deploy_car_h: 'Dans la voiture', deploy_car_badge: 'Idéal pour un seul utilisateur',
    deploy_car_p: "L'ESP32 et la télécommande voyagent dans la voiture, alimentés par le port USB. Le BLE fonctionne toujours car tout est dans le même véhicule.",
    deploy_car_li1: "Se déclenche automatiquement à l'approche du portail",
    deploy_car_li2: 'Se charge quand la voiture est allumée',
    deploy_car_li3: "Rien à installer ni à entretenir au garage",
    deploy_ga_h: 'Au garage', deploy_ga_badge: 'Multi-utilisateurs',
    deploy_ga_p: "L'ESP32 vit près du portail, alimenté par prise murale, batterie ou solaire. Quiconque possède le mot de passe peut ouvrir le portail depuis son propre téléphone.",
    deploy_ga_li1: 'Supporte plusieurs utilisateurs et véhicules',
    deploy_ga_li2: 'Veille profonde entre les sondages (~12 mois sur batterie)',
    deploy_ga_li3: 'Portée BLE 10–30 m à travers les murs — planifiez le placement',
    sec_h2: 'Votre mot de passe', sec_h2_b: 'ne quitte jamais le téléphone',
    sec_p: "Chaque connexion génère un nonce aléatoire sur l'ESP32. Le téléphone hache le nonce avec votre mot de passe via HMAC-SHA256 et n'envoie que le hash. L'ESP32 le vérifie localement. Un paquet capturé est inutile.",
    sec_link: 'Lire le modèle de sécurité',
    cta_h2: 'Prêt à ouvrir en mode mains libres ?',
    cta_p: "Téléchargez l'app Android, l'outil de flash, et c'est opérationnel en une étape.",
    cta_play: 'Télécharger sur Google Play',
    cta_docs: 'Lire la documentation',
    nav_back: '← Retour à l\'accueil',
    nav_features: 'Fonctionnalités',
    nav_how:      'Comment ça marche',
    nav_hardware: 'Matériel',
    nav_docs:     'Documentation',
    docs_title: 'Guide de configuration et référence',
    docs_sub: "Tout ce qu'il faut pour flasher l'ESP32 et repartir avec un portail qui s'ouvre tout seul.",
    docs_qs_h: 'Démarrage rapide',
    docs_qs_p: 'Trois étapes du déballage à un ouvre-porte fonctionnel.',
    docs_step1_h: 'Flasher le firmware', docs_step1_p: "Téléchargez GarageAAtoESP32-flash-tool.zip depuis la page Releases, décompressez-le et double-cliquez sur flash.bat.",
    docs_step2_h: "Installer l'app Android", docs_step2_p: 'Téléchargez-la sur Google Play ou installez l\'APK depuis la page Releases.',
    docs_step3_h: 'Coupler et démarrer', docs_step3_p: "Ouvrez Garage Opener sur votre téléphone — il vous guidera pour scanner l'ESP32 et saisir votre mot de passe. Connectez ensuite votre téléphone à Android Auto et appuyez sur Ouvrir le garage.",
    docs_stuck_h: 'Toujours bloqué ?', docs_stuck_p: 'Ouvrez une issue sur GitHub en incluant la sortie du moniteur série et le type de carte.',
    docs_issue: 'Ouvrir une issue',
  },

  de: {
    label: 'Deutsch', flag: 'DE',
    hero_chip:    'Keine Cloud · Kein Abo · Kein WLAN',
    hero_h1_a:   'Deine Garage öffnet sich',
    hero_h1_b:   'bevor du das Auto anhältst',
    hero_sub:    'Ein Tipp auf deinem Android Auto-Bildschirm — oder sag',
    hero_sub_em: '"Garage öffnen"',
    hero_sub2:   '— und der ESP32 drückt die Fernbedienung für dich. Oder fahr einfach vor: die Geofence öffnet es, bevor du anhältst. Kein Internet. Keine Cloud.',
    hero_cta_play:   'Bei Google Play herunterladen',
    hero_cta_github: 'GitHub',
    ticker: ['Android Auto','ESP32 BLE','Kein Internet','HMAC-SHA256-Auth','Open Source','Sprachbefehle','Geofence-Automatik','Kein Löten','Schnelleinstellungs-Kachel','Tiefer Schlaf'],
    who_chip: 'Die Situation',
    who_h2_a: 'Gemeinschaftsgarage.',
    who_h2_b: 'Geliehener Schlüssel.',
    who_p1: 'Du nutzt eine Gemeinschaftsgarage — ein Wohnblock, ein gemieteter Stellplatz — wo du den Motor oder den Torantrieb nicht anfassen kannst. Du hast nur eine Fernbedienung.',
    who_p2: 'Das gibt dir eine freihändige Möglichkeit, das Tor vom Android Auto-Bildschirm deines Autos aus zu öffnen. Kein Internet. Keine Cloud. Kein Hub. Der ESP32 drückt den Knopf der Fernbedienung für dich.',
    stat_ble: '< 1s', stat_ble_l: 'typische BLE-Roundtrip-Zeit',
    stat_lang: '8', stat_lang_l: 'Unterstützte Sprachen',
    stat_cloud: '0', stat_cloud_l: 'Cloud-Abhängigkeiten',
    stat_trigger: '3', stat_trigger_l: 'Auslöseroptionen (inkl. ohne Löten)',
    feat_chip: 'Richtig gemacht',
    feat_h2_a: 'Alles, was du erwartest.',
    feat_h2_b: 'Nichts, was du nicht brauchst.',
    feat1_h: 'HMAC-SHA256-Authentifizierung', feat1_p: 'Dein Passwort überquert nie die Luft — nur ein Hash eines frischen Zufalls-Nonce. Replay-Angriffe sind durch das Design unmöglich.',
    feat2_h: 'Android Auto nativ',             feat2_p: 'Gebaut auf der Car App Library. Ein einziger Knopf auf dem Autobildschirm — sicher aus dem Fahrersitz erkennbar.',
    feat3_h: 'Sprachbefehle',                  feat3_p: 'Sag "Garage öffnen" ins Mikrofon des Autos — Google leitet die Absicht direkt an den BLE-Befehl weiter. In allen 8 Sprachen.',
    feat4_h: 'Geofence-Automatik',             feat4_p: 'Lege in den Einstellungen einen Radius auf der Karte fest. Wenn du die Zone mit verbundenem Android Auto betrittst, öffnet sich das Tor automatisch — ohne Tippen. Eine Benachrichtigung bestätigt jeden automatischen Auslöser.',
    feat5_h: 'Live-Präsenzanzeige',            feat5_p: 'Ein stromsparender Hintergrund-BLE-Scan zeigt einen grünen Punkt, wenn der Öffner in Reichweite ist.',
    feat6_h: 'Mieterfreundliche Option',       feat6_p: 'Fernbedienung nicht modifizierbar? Option B schaltet die Batterieversorgung per Relais — Kupferband-Scheiben im Batteriefach, kein Löten innen. Vollständig reversibel.',
    how_chip: 'Der Ablauf',
    how_h2_a: 'Vom Telefon zum Tor',
    how_h2_b: 'in unter einer Sekunde',
    step1_h: 'Du tippst auf den Autobildschirm (oder sagst es)', step1_p: 'Ein Knopf auf deinem Android Auto-Display. Oder frag Google — "Garage öffnen" funktioniert in 8 Sprachen.',
    step2_h: 'Telefon sendet HMAC-Befehl per BLE',              step2_p: 'Die App hasht einen Zufalls-Nonce mit deinem Passwort und sendet nur den Hash — das Passwort verlässt nie das Telefon.',
    step3_h: 'ESP32 verifiziert und löst aus',                   step3_p: 'Der ESP32 in deiner Garage validiert den HMAC, löst das Relais aus und sendet eine Statusbenachrichtigung zurück.',
    step4_h: 'Bildschirm bestätigt — Tor öffnet sich',           step4_p: 'Der Ring wird bei Erfolg grün, zeigt bei Fehler "Erneut versuchen". Typische Roundtrip-Zeit: unter 1 Sekunde.',
    hw_chip: 'Wähle dein Setup',
    hw_h2_a: 'Drei Auslöseroptionen.',
    hw_h2_b: 'Eine Firmware.',
    hw_sub: 'Die ESP32-Firmware unterstützt alle drei — wähle die, die zu deiner Situation passt.',
    hw_a_h: 'Relaismodul', hw_a_badge: 'Empfohlen',
    hw_a_p: 'Ein kleines Relaismodul schließt die Tastenfelder der Fernbedienung kurz. Einsteigerfreundlich — der integrierte Treiber übernimmt alles. 3V-Spule (z.B. Songle SRD-03VDC-SL-C) direkt am 3,3V-Rail des ESP32.',
    hw_a_t1: '~1 $', hw_a_t2: 'Löten erforderlich',
    hw_b_h: 'Relais + Stromschiene', hw_b_badge: 'Mieterfreundlich',
    hw_b_p: 'Gleiches Relaismodul wie A, schaltet aber die Batterie-Masseschiene der Fernbedienung. Kupferband-Scheiben im Batteriefach — kein Löten im Inneren. Vollständig reversibel.',
    hw_b_t1: 'Keine Modifikation', hw_b_t2: '~1 $',
    hw_c_h: 'Kapazitiver Impuls', hw_c_badge: 'Kein Löten',
    hw_c_p: 'Der ESP32 sendet einen Ladeimpuls an ein Stück Kupferband auf dem kapazitiven Knopf eines Fingerbot Plus — der dann mechanisch die Fernbedienung drückt. Nichts wird geöffnet oder gelötet.',
    hw_c_t1: 'Mieterfreundlich', hw_c_t2: '+0,5–1s Latenz',
    deploy_chip: 'Einsatz',
    deploy_h2_a: 'Im Auto oder an',
    deploy_h2_b: 'der Garageneinfahrt',
    deploy_car_h: 'Im Auto', deploy_car_badge: 'Ideal für Einzelnutzer',
    deploy_car_p: 'ESP32 und Fernbedienung fahren im Auto, gespeist vom USB-Anschluss. BLE funktioniert immer, da alles im gleichen Fahrzeug ist.',
    deploy_car_li1: 'Löst automatisch aus, wenn du ans Tor fährst',
    deploy_car_li2: 'Lädt, wenn das Auto läuft',
    deploy_car_li3: 'Nichts in der Garage zu installieren oder zu warten',
    deploy_ga_h: 'An der Garage', deploy_ga_badge: 'Mehrbenutzer',
    deploy_ga_p: 'Der ESP32 befindet sich am Tor, gespeist von Steckdose, Powerbank oder Solar. Jeder, dem du das Passwort gibst, kann das Tor von seinem eigenen Telefon aus öffnen.',
    deploy_ga_li1: 'Unterstützt mehrere Benutzer und Fahrzeuge',
    deploy_ga_li2: 'Tiefer Schlaf zwischen Abfragen (~12 Monate mit Powerbank)',
    deploy_ga_li3: 'BLE-Reichweite 10–30 m durch Wände — Platzierung planen',
    sec_h2: 'Dein Passwort', sec_h2_b: 'verlässt nie das Telefon',
    sec_p: 'Jede Verbindung generiert einen frischen Zufalls-Nonce auf dem ESP32. Das Telefon hasht den Nonce mit deinem Passwort via HMAC-SHA256 und sendet nur den Hash. Der ESP32 verifiziert ihn lokal. Ein abgefangenes Paket ist wertlos.',
    sec_link: 'Sicherheitsmodell lesen',
    cta_h2: 'Bereit zum freihändigen Öffnen?',
    cta_p: 'Lade die Android-App und das Flash-Tool herunter — in einem Schritt einsatzbereit.',
    cta_play: 'Bei Google Play herunterladen',
    cta_docs: 'Dokumentation lesen',
    nav_back: '← Zurück zur Startseite',
    nav_features: 'Funktionen',
    nav_how:      'So funktioniert es',
    nav_hardware: 'Hardware',
    nav_docs:     'Dokumentation',
    docs_title: 'Einrichtungsanleitung & Referenz',
    docs_sub: 'Alles vom Flashen des ESP32 bis zur Einfahrt mit automatisch öffnendem Tor.',
    docs_qs_h: 'Schnellstart',
    docs_qs_p: 'Drei Schritte vom Auspacken zum funktionierenden Garagenöffner.',
    docs_step1_h: 'Firmware flashen', docs_step1_p: 'Lade GarageAAtoESP32-flash-tool.zip von der Releases-Seite herunter, entpacke es und doppelklicke auf flash.bat.',
    docs_step2_h: 'Android-App installieren', docs_step2_p: 'Lade sie aus dem Play Store herunter oder installiere die APK von der Releases-Seite.',
    docs_step3_h: 'Koppeln und loslegen', docs_step3_p: 'Öffne Garage Opener auf deinem Telefon — es führt dich durch das Scannen des ESP32 und die Eingabe deines Passworts. Verbinde dann dein Telefon mit Android Auto und tippe auf Garage öffnen.',
    docs_stuck_h: 'Immer noch nicht weiter?', docs_stuck_p: 'Erstelle ein Issue auf GitHub und füge die Ausgabe des seriellen Monitors und den Boardtyp hinzu.',
    docs_issue: 'Issue erstellen',
  },

  ca: {
    label: 'Català', flag: 'CA',
    hero_chip:    'Sense núvol · Sense subscripció · Sense Wi-Fi',
    hero_h1_a:   'El teu garatge s\'obre',
    hero_h1_b:   'abans que aturis el cotxe',
    hero_sub:    'Un toc a la pantalla d\'Android Auto — o digues',
    hero_sub_em: '"Obre el garatge"',
    hero_sub2:   '— i l\'ESP32 prem el comandament per tu. O simplement acosta\'t: el geofence l\'obre abans que aturis. Sense internet. Sense núvol.',
    hero_cta_play: 'Descarrega a Google Play',
    hero_cta_github: 'GitHub',
    ticker: ['Android Auto','ESP32 BLE','Sense internet','Auth HMAC-SHA256','Codi obert','Ordres de veu','Obertura per geofence','Sense soldadura','Accés ràpid','Firmware baix consum'],
    who_chip: 'La situació',
    who_h2_a: 'Garatge compartit.',
    who_h2_b: 'Comandament prestat.',
    who_p1: "Fas servir un garatge comunitari — un bloc de pisos, un espai llogat — on no pots tocar el motor ni el controlador de la porta. Només tens un comandament.",
    who_p2: "Això et dona una manera de mans lliures per obrir la porta des de la pantalla d'Android Auto del teu cotxe. Sense internet. Sense núvol. Sense hub. L'ESP32 prem el botó del comandament per tu.",
    stat_ble: '< 1s', stat_ble_l: 'anada i tornada BLE típica',
    stat_lang: '8', stat_lang_l: 'Idiomes suportats',
    stat_cloud: '0', stat_cloud_l: 'Dependències al núvol',
    stat_trigger: '3', stat_trigger_l: 'Opcions de disparador (incl. sense soldadura)',
    feat_chip: 'Ben fet',
    feat_h2_a: 'Tot el que esperaries.',
    feat_h2_b: 'Res que no necessitis.',
    feat1_h: 'Autenticació HMAC-SHA256', feat1_p: 'La teva contrasenya no viatja mai per l\'aire — només el hash d\'un nonce aleatori. Els atacs de repetició són impossibles per disseny.',
    feat2_h: 'Android Auto natiu',       feat2_p: 'Construït sobre la Car App Library. Un sol botó a la pantalla del cotxe — segur de veure des del seient del conductor.',
    feat3_h: 'Ordres de veu',            feat3_p: '"Obre el garatge" per al micròfon del cotxe — Google enruta la intenció directament a l\'ordre BLE. En els 8 idiomes.',
    feat4_h: 'Obertura automàtica per geofence', feat4_p: 'Defineix un radi al mapa des d\'Ajustos. En entrar a la zona amb Android Auto connectat, la porta s\'obre sola — sense tocar res. Una notificació confirma cada disparo automàtic.',
    feat5_h: 'Indicador de presència',   feat5_p: 'Un escàner BLE de baix consum mostra un punt verd quan l\'obrador és a l\'abast — tant al telèfon com al cotxe.',
    feat6_h: 'Opció per a llogaters',   feat6_p: 'No pots modificar el comandament? L\'opció B usa un relé per commutlar l\'alimentació — discos de cinta de coure al compartiment, sense soldar dins. Totalment reversible.',
    how_chip: 'El flux',
    how_h2_a: 'Del telèfon a la porta',
    how_h2_b: 'en menys d\'un segon',
    step1_h: 'Toques la pantalla (o ho dius)',                      step1_p: 'Un botó a la teva pantalla d\'Android Auto. O pregunta a Google — "Obre el garatge" funciona en 8 idiomes.',
    step2_h: 'El telèfon envia una ordre HMAC per BLE',             step2_p: 'L\'app aplica hash a un nonce aleatori amb la teva contrasenya i envia només el hash.',
    step3_h: 'L\'ESP32 verifica i actua',                           step3_p: 'L\'ESP32 al teu garatge valida el HMAC, activa el relé i retorna una notificació d\'estat.',
    step4_h: 'La pantalla confirma — la porta s\'obre',             step4_p: 'L\'anell es posa verd amb èxit, mostra "Torna-ho a provar" en cas de fallada. Temps de viatge típic: menys d\'un segon.',
    hw_chip: 'Tria la teva configuració',
    hw_h2_a: 'Tres opcions de disparador.',
    hw_h2_b: 'Un firmware.',
    hw_sub: 'El firmware de l\'ESP32 gestiona les tres — tria la que s\'adapti a la teva situació.',
    hw_a_h: 'Mòdul relé', hw_a_badge: 'Recomanat',
    hw_a_p: 'Un petit mòdul relé curtcircuita els pads del botó del comandament. Fàcil per a principiants — el driver integrat ho gestiona tot. Bobina de 3V (p.ex. Songle SRD-03VDC-SL-C) directament al rail 3.3V de l\'ESP32.',
    hw_a_t1: '~$1–2', hw_a_t2: 'Soldadura requerida',
    hw_b_h: 'Relé + commutació d\'alimentació', hw_b_badge: 'Per a llogaters',
    hw_b_p: 'Mateix mòdul relé que A, però commuta el rail de bateria del comandament. Discos de cinta de coure a la ranura de la pila — sense soldar dins del comandament. Totalment reversible.',
    hw_b_t1: 'Sense modificar el comandament', hw_b_t2: '~$1–2',
    hw_c_h: 'Pols capacitiu', hw_c_badge: 'Sense soldadura',
    hw_c_p: 'L\'ESP32 envia un pols de càrrega a un tros de cinta de coure sobre el botó capacitiu d\'un Fingerbot Plus — que prem mecànicament el comandament. Res s\'obre ni es solda.',
    hw_c_t1: 'Per a llogaters', hw_c_t2: '+0,5–1s de latència',
    deploy_chip: 'Desplegament',
    deploy_h2_a: 'Al cotxe o a',
    deploy_h2_b: "l'entrada del garatge",
    deploy_car_h: 'Al cotxe', deploy_car_badge: 'Ideal per a un sol usuari',
    deploy_car_p: "L'ESP32 i el comandament van al cotxe, alimentats per l'USB. El BLE sempre funciona perquè tot és al mateix vehicle.",
    deploy_car_li1: "S'activa automàticament en arribar a la porta",
    deploy_car_li2: "Es carrega quan el cotxe és engegat",
    deploy_car_li3: "Res a instal·lar ni mantenir al garatge",
    deploy_ga_h: 'Al garatge', deploy_ga_badge: 'Multiusuari',
    deploy_ga_p: "L'ESP32 viu a prop de la porta, alimentat per endoll, bateria o solar. Qualsevol amb qui comparteixis la contrasenya pot obrir la porta des del seu telèfon.",
    deploy_ga_li1: 'Suporta múltiples usuaris i vehicles',
    deploy_ga_li2: 'Deep sleep entre sondejos (~12 mesos amb bateria)',
    deploy_ga_li3: 'Rang BLE 10–30 m a través de parets — planifica la ubicació',
    sec_h2: 'La teva contrasenya', sec_h2_b: 'mai surt del telèfon',
    sec_p: "Cada connexió genera un nonce aleatori nou a l'ESP32. El telèfon aplica HMAC-SHA256 al nonce amb la teva contrasenya i envia només el hash. L'ESP32 el verifica localment.",
    sec_link: 'Llegir el model de seguretat',
    cta_h2: 'Preparat per obrir amb mans lliures?',
    cta_p: "Descarrega l'app Android i l'eina de flash — en un pas ja funciona.",
    cta_play: 'Descarrega a Google Play',
    cta_docs: 'Llegir la documentació',
    nav_back: '← Tornar a l\'inici',
    nav_features: 'Funcionalitats',
    nav_how:      'Com funciona',
    nav_hardware: 'Maquinari',
    nav_docs:     'Documentació',
    docs_title: 'Guia de configuració i referència',
    docs_sub: 'Tot el necessari per flashejar l\'ESP32 i sortir conduint amb la porta que s\'obre sola.',
    docs_qs_h: 'Inici ràpid',
    docs_qs_p: 'Tres passos des del unboxing fins a un obrador funcionant.',
    docs_step1_h: 'Flashejar el firmware', docs_step1_p: 'Descarrega GarageAAtoESP32-flash-tool.zip des de la pàgina Releases, descomprimeix-lo i fes doble clic a flash.bat.',
    docs_step2_h: "Instal·lar l'app Android", docs_step2_p: "Descarrega-la de Google Play o instal·la l'APK des de la pàgina Releases.",
    docs_step3_h: 'Vincular i llest', docs_step3_p: "Obre Garage Opener al teu telèfon — et guiarà per escanejar l'ESP32 i introduir la teva contrasenya. Connecta el telèfon a Android Auto i prem Obrir garatge.",
    docs_stuck_h: 'Encara encallat?', docs_stuck_p: 'Obre un issue a GitHub i inclou la sortida del monitor sèrie i el tipus de placa.',
    docs_issue: 'Obrir un issue',
  },

  it: {
    label: 'Italiano', flag: 'IT',
    hero_chip:    'Nessun cloud · Nessun abbonamento · Nessun Wi-Fi',
    hero_h1_a:   'Il tuo garage si apre',
    hero_h1_b:   'prima che tu spenga il motore',
    hero_sub:    'Un tocco sullo schermo di Android Auto — o di\'',
    hero_sub_em: '"Apri il garage"',
    hero_sub2:   '— e l\'ESP32 preme il telecomando al posto tuo. O guida e basta: il geofence lo apre prima che tu fermi. Senza internet. Senza cloud.',
    hero_cta_play: 'Scarica su Google Play',
    hero_cta_github: 'GitHub',
    ticker: ['Android Auto','ESP32 BLE','Senza internet','Auth HMAC-SHA256','Open source','Comandi vocali','Apertura geofence','Senza saldatura','Riquadro accesso rapido','Firmware basso consumo'],
    who_chip: 'La situazione',
    who_h2_a: 'Garage condiviso.',
    who_h2_b: 'Telecomando in prestito.',
    who_p1: "Usi un garage in comune — un condominio, uno spazio in affitto — dove non puoi toccare il motore o il controller del cancello. Hai solo un telecomando.",
    who_p2: "Questo ti dà un modo a mani libere per aprire la porta dallo schermo Android Auto della tua auto. Senza internet. Senza cloud. Senza hub. L'ESP32 preme il pulsante del telecomando al posto tuo.",
    stat_ble: '< 1s', stat_ble_l: 'andata e ritorno BLE tipico',
    stat_lang: '8', stat_lang_l: 'Lingue supportate',
    stat_cloud: '0', stat_cloud_l: 'Dipendenze cloud',
    stat_trigger: '3', stat_trigger_l: 'Opzioni di attivazione (incl. senza saldatura)',
    feat_chip: 'Fatto bene',
    feat_h2_a: 'Tutto quello che ti aspetti.',
    feat_h2_b: 'Niente di superfluo.',
    feat1_h: 'Autenticazione HMAC-SHA256', feat1_p: 'La tua password non viaggia mai nell\'aria — solo l\'hash di un nonce casuale. Gli attacchi di replay sono impossibili per design.',
    feat2_h: 'Android Auto nativo',        feat2_p: 'Costruito sulla Car App Library. Un solo pulsante sullo schermo dell\'auto — sicuro da guardare dal sedile del conducente.',
    feat3_h: 'Comandi vocali',             feat3_p: 'Di\' "Apri il garage" nel microfono dell\'auto — Google instrada l\'intent direttamente al comando BLE. In tutte le 8 lingue.',
    feat4_h: 'Apertura automatica geofence', feat4_p: 'Imposta un raggio sulla mappa nelle impostazioni. Quando entri nella zona con Android Auto connesso, il cancello si apre da solo — senza toccare nulla. Una notifica conferma ogni attivazione automatica.',
    feat5_h: 'Indicatore di presenza',     feat5_p: 'Una scansione BLE in background a basso consumo mostra un punto verde quando l\'apriporta è nel raggio.',
    feat6_h: 'Opzione affittuari',         feat6_p: 'Non puoi modificare il telecomando? L\'opzione B usa un relè per commutare l\'alimentazione — dischi di nastro di rame nel vano batteria, nessuna saldatura interna. Completamente reversibile.',
    how_chip: 'Il flusso',
    how_h2_a: 'Dal telefono al cancello',
    how_h2_b: 'in meno di un secondo',
    step1_h: 'Tocchi lo schermo (o lo dici)',                       step1_p: 'Un pulsante sul tuo display Android Auto. O chiedi a Google — "Apri il garage" funziona in 8 lingue.',
    step2_h: 'Il telefono invia un comando HMAC via BLE',           step2_p: 'L\'app applica hash a un nonce casuale con la tua password e invia solo l\'hash — la password non lascia mai il telefono.',
    step3_h: 'L\'ESP32 verifica e agisce',                          step3_p: 'L\'ESP32 nel tuo garage valida l\'HMAC, attiva il relè e invia una notifica di stato.',
    step4_h: 'Lo schermo conferma — il cancello si apre',           step4_p: 'L\'anello diventa verde in caso di successo, mostra "Riprova" in caso di errore. Andata e ritorno tipico: meno di 1 secondo.',
    hw_chip: 'Scegli il tuo setup',
    hw_h2_a: 'Tre opzioni di attivazione.',
    hw_h2_b: 'Un solo firmware.',
    hw_sub: 'Il firmware ESP32 gestisce tutte e tre — scegli quella adatta alla tua situazione.',
    hw_a_h: 'Modulo relè', hw_a_badge: 'Consigliato',
    hw_a_p: 'Un piccolo modulo relè cortocircuita i pad del pulsante del telecomando. Adatto ai principianti — il driver integrato gestisce tutto. Bobina 3V (es. Songle SRD-03VDC-SL-C) direttamente sul rail 3,3V dell\'ESP32.',
    hw_a_t1: '~$1–2', hw_a_t2: 'Saldatura richiesta',
    hw_b_h: 'Relè + commutazione alimentazione', hw_b_badge: 'Per chi affitta',
    hw_b_p: 'Stesso modulo relè di A, ma commuta il rail della batteria del telecomando. Dischi di nastro di rame nello slot della batteria — nessuna saldatura all\'interno. Completamente reversibile.',
    hw_b_t1: 'Nessuna modifica', hw_b_t2: '~$1–2',
    hw_c_h: 'Impulso capacitivo', hw_c_badge: 'Senza saldatura',
    hw_c_p: 'L\'ESP32 invia un impulso di carica a un pezzo di nastro di rame sul pulsante capacitivo di un Fingerbot Plus — che poi preme meccanicamente il telecomando.',
    hw_c_t1: 'Per affittuari', hw_c_t2: '+0,5–1s di latenza',
    deploy_chip: 'Distribuzione',
    deploy_h2_a: 'In auto o all\'',
    deploy_h2_b: 'ingresso del garage',
    deploy_car_h: 'In auto', deploy_car_badge: 'Ideale per uso singolo',
    deploy_car_p: 'ESP32 e telecomando viaggiano in auto, alimentati dalla porta USB. Il BLE funziona sempre perché tutto è nello stesso veicolo.',
    deploy_car_li1: 'Si attiva automaticamente avvicinandosi al cancello',
    deploy_car_li2: 'Si ricarica quando l\'auto è accesa',
    deploy_car_li3: 'Nulla da installare o manutenere al garage',
    deploy_ga_h: 'Al garage', deploy_ga_badge: 'Multi-utente',
    deploy_ga_p: 'L\'ESP32 vive vicino al cancello, alimentato da presa, powerbank o solare. Chiunque abbia la password può aprire il cancello dal proprio telefono.',
    deploy_ga_li1: 'Supporta più utenti e veicoli',
    deploy_ga_li2: 'Deep sleep tra i polling (~12 mesi con powerbank)',
    deploy_ga_li3: 'Raggio BLE 10–30 m attraverso i muri — pianifica il posizionamento',
    sec_h2: 'La tua password', sec_h2_b: 'non lascia mai il telefono',
    sec_p: 'Ogni connessione genera un nonce casuale sull\'ESP32. Il telefono applica HMAC-SHA256 al nonce con la tua password e invia solo l\'hash. L\'ESP32 lo verifica localmente. Un pacchetto catturato è inutile.',
    sec_link: 'Leggi il modello di sicurezza',
    cta_h2: 'Pronto ad aprire a mani libere?',
    cta_p: 'Scarica l\'app Android e il flash tool — operativo in un solo passaggio.',
    cta_play: 'Scarica su Google Play',
    cta_docs: 'Leggi la documentazione',
    nav_back: '← Torna alla home',
    nav_features: 'Funzionalità',
    nav_how:      'Come funziona',
    nav_hardware: 'Hardware',
    nav_docs:     'Documentazione',
    docs_title: 'Guida alla configurazione e riferimento',
    docs_sub: 'Tutto il necessario per flashare l\'ESP32 e partire con un cancello che si apre da solo.',
    docs_qs_h: 'Avvio rapido',
    docs_qs_p: 'Tre passi dall\'unboxing a un apriporta funzionante.',
    docs_step1_h: 'Flash del firmware', docs_step1_p: 'Scarica GarageAAtoESP32-flash-tool.zip dalla pagina Releases, estrailo e fai doppio clic su flash.bat.',
    docs_step2_h: 'Installa l\'app Android', docs_step2_p: 'Scaricala dal Play Store o installa l\'APK dalla pagina Releases.',
    docs_step3_h: 'Abbina e vai', docs_step3_p: 'Apri Garage Opener sul tuo telefono — ti guiderà nella scansione dell\'ESP32 e nell\'inserimento della password. Connetti il telefono ad Android Auto e tocca Apri garage.',
    docs_stuck_h: 'Ancora bloccato?', docs_stuck_p: 'Apri una issue su GitHub includendo l\'output del monitor seriale e il tipo di scheda.',
    docs_issue: 'Apri una issue',
  },

  fi: {
    label: 'Suomi', flag: 'FI',
    hero_chip:    'Ei pilveä · Ei tilausta · Ei Wi-Fiä',
    hero_h1_a:   'Autotallisi aukeaa',
    hero_h1_b:   'ennen kuin pysäytät auton',
    hero_sub:    'Yksi napautus Android Auto -näytöllä — tai sano',
    hero_sub_em: '"Avaa autotalli"',
    hero_sub2:   '— ja ESP32 painaa kaukosäädintä puolestasi. Tai aja vain paikalle: geofence avaa sen ennen kuin pysähdyt. Ei internetiä. Ei pilveä.',
    hero_cta_play: 'Lataa Google Playsta',
    hero_cta_github: 'GitHub',
    ticker: ['Android Auto','ESP32 BLE','Ei internetiä','HMAC-SHA256-todennus','Avoin lähdekoodi','Äänikomennot','Geofence-automaatio','Ei juottamista','Pika-asetus-ruutu','Syväuni-laiteohjelmisto'],
    who_chip: 'Tilanne',
    who_h2_a: 'Yhteinen autotalli.',
    who_h2_b: 'Lainattu kaukosäädin.',
    who_p1: 'Käytät yhteistä autotallia — kerrostalo, vuokrattu paikka — jossa et voi koskea moottoriin tai portin ohjaimeen. Sinulla on vain kaukosäädin.',
    who_p2: 'Tämä antaa sinulle handsfree-tavan avata ovi auton Android Auto -näytöltä. Ei internetiä. Ei pilveä. Ei hub-laitetta. ESP32 painaa kaukosäätimen nappia puolestasi.',
    stat_ble: '< 1s', stat_ble_l: 'tyypillinen BLE-edestakaisin-aika',
    stat_lang: '8', stat_lang_l: 'Tuetut kielet',
    stat_cloud: '0', stat_cloud_l: 'Pilviriippuvuudet',
    stat_trigger: '3', stat_trigger_l: 'Laukaisuvaihtoehtoja (ml. juottamaton)',
    feat_chip: 'Tehty oikein',
    feat_h2_a: 'Kaikki mitä odottaisit.',
    feat_h2_b: 'Ei mitään turhaa.',
    feat1_h: 'HMAC-SHA256-todennus', feat1_p: 'Salasanasi ei kulje koskaan ilmassa — vain satunnaisen noncen hash. Toistohyökkäykset ovat suunnittelultaan mahdottomia.',
    feat2_h: 'Android Auto natiivisti', feat2_p: 'Rakennettu Car App Librarylle. Yksi nappi auton näytöllä — turvallinen vilkaista kuljettajan paikalta.',
    feat3_h: 'Äänikomennot', feat3_p: '"Avaa autotalli" auton mikrofoniin — Google ohjaa aikeen suoraan BLE-komentoon. Kaikilla 8 kielellä.',
    feat4_h: 'Geofence-automaatio',           feat4_p: 'Aseta säde kartalla asetuksissa. Kun saavut alueelle Android Auto yhdistettynä, portti aukeaa automaattisesti — ilman kosketuksia. Ilmoitus vahvistaa jokaisen automaattisen laukaisun.',
    feat5_h: 'Live-läsnäoloindikaattori', feat5_p: 'Matalaenerginen tausta-BLE-skannaus näyttää vihreän pisteen kun avain on kantamassa — sekä puhelimessa että autossa.',
    feat6_h: 'Vuokraajaystävällinen vaihtoehto', feat6_p: 'Etkö voi muokata kaukosäädintä? Vaihtoehto B käyttää relettä akkuvirran kytkemiseen — kupariteipin kiekot akkupaikkaan, ei juottamista sisälle. Täysin palautettavissa.',
    how_chip: 'Kulku',
    how_h2_a: 'Puhelimesta portille',
    how_h2_b: 'alle sekunnissa',
    step1_h: 'Napautat näyttöä (tai sanot sen)', step1_p: 'Yksi nappi Android Auto -näytöllä. Tai kysy Googlelta — "Avaa autotalli" toimii 8 kielellä.',
    step2_h: 'Puhelin lähettää HMAC-komennon BLE:n kautta', step2_p: 'Sovellus tiivistää satunnaisen noncen salasanallasi ja lähettää vain tiivisteen — salasana ei koskaan poistu puhelimesta.',
    step3_h: 'ESP32 tarkistaa ja laukaisee', step3_p: 'Autotallisi ESP32 vahvistaa HMAC:n, laukaisee releen ja lähettää tilasilmoituksen takaisin.',
    step4_h: 'Näyttö vahvistaa — portti aukeaa', step4_p: 'Rengas muuttuu vihreäksi onnistuessa, näyttää "Yritä uudelleen" epäonnistuessa. Tyypillinen edestakaisin-aika: alle 1 sekunti.',
    hw_chip: 'Valitse asetuksesi',
    hw_h2_a: 'Kolme laukaisuvaihtoehtoa.',
    hw_h2_b: 'Yksi laiteohjelmisto.',
    hw_sub: 'ESP32-laiteohjelmisto käsittelee kaikki kolme — valitse tilanteellesi sopiva.',
    hw_a_h: 'Reletmoduuli', hw_a_badge: 'Suositeltu',
    hw_a_p: 'Pieni reletmoduuli oikosulkee kaukosäätimen nappilevyt. Aloittelijaystävällinen — integroitu ajuri hoitaa kaiken. Käytä 3V-kelaa (esim. Songle SRD-03VDC-SL-C) suoraan ESP32:n 3,3V-linjaan.',
    hw_a_t1: '~$1–2', hw_a_t2: 'Juottaminen vaaditaan',
    hw_b_h: 'Rele + virrankytkentä', hw_b_badge: 'Vuokralaisystävällinen',
    hw_b_p: 'Sama reletmoduuli kuin A, mutta kytkee kaukosäätimen akkurailin. Kupariteipin kiekot akkupaikkaan — ei juottamista sisälle. Täysin palautettavissa.',
    hw_b_t1: 'Ei muutoksia laitteeseen', hw_b_t2: '~$1–2',
    hw_c_h: 'Kapasitiivinen pulssi', hw_c_badge: 'Ei juottamista',
    hw_c_p: 'ESP32 lähettää latausspulssin kupariteippikappaleeseen Fingerbot Plus -laitteen kapasitiivisen napin päällä — joka painaa kaukosäädintä mekaanisesti.',
    hw_c_t1: 'Vuokraajaystävällinen', hw_c_t2: '+0,5–1s viive',
    deploy_chip: 'Käyttöönotto',
    deploy_h2_a: 'Autossa tai',
    deploy_h2_b: 'autotallin sisäänkäynnillä',
    deploy_car_h: 'Autossa', deploy_car_badge: 'Paras yksittäiskäyttöön',
    deploy_car_p: 'ESP32 ja kaukosäädin matkustavat autossa, USB-portista virransaannilla. BLE toimii aina koska kaikki on samassa ajoneuvossa.',
    deploy_car_li1: 'Laukaisee automaattisesti saapuessasi portille',
    deploy_car_li2: 'Latautuu kun auto on käynnissä',
    deploy_car_li3: 'Ei asennettavaa eikä ylläpidettävää autotallissa',
    deploy_ga_h: 'Autotallissa', deploy_ga_badge: 'Monikäyttäjä',
    deploy_ga_p: 'ESP32 asuu portin lähellä, virransaannilla pistokkeesta, powerbankilta tai auringosta. Kuka tahansa jolle jaat salasanan voi avata portin omalta puhelimeltaan.',
    deploy_ga_li1: 'Tukee useita käyttäjiä ja ajoneuvoja',
    deploy_ga_li2: 'Syväuni kyselyjen välillä (~12 kk powerbankilta)',
    deploy_ga_li3: 'BLE-kantama 10–30 m seinien läpi — suunnittele sijoitus',
    sec_h2: 'Salasanasi', sec_h2_b: 'ei poistu koskaan puhelimesta',
    sec_p: 'Jokainen yhteys luo uuden satunnaisen noncen ESP32:lla. Puhelin tiivistää noncen salasanallasi HMAC-SHA256:lla ja lähettää vain tiivisteen. ESP32 tarkistaa sen paikallisesti. Kaapattu paketti on arvoton.',
    sec_link: 'Lue tietoturvamalli',
    cta_h2: 'Valmis avaamaan handsfree?',
    cta_p: 'Lataa Android-sovellus ja flash-työkalu — toiminnassa yhdessä vaiheessa.',
    cta_play: 'Lataa Google Playsta',
    cta_docs: 'Lue dokumentaatio',
    nav_back: '← Takaisin etusivulle',
    nav_features: 'Ominaisuudet',
    nav_how:      'Kuinka se toimii',
    nav_hardware: 'Laitteisto',
    nav_docs:     'Dokumentaatio',
    docs_title: 'Asennusopas ja viite',
    docs_sub: 'Kaikki ESP32:n flashauksesta lähtöön automaattisesti aukeavalla portilla.',
    docs_qs_h: 'Pikaopas',
    docs_qs_p: 'Kolme vaihetta pakkauksesta toimivaan avaajaan.',
    docs_step1_h: 'Flashaa laiteohjelmisto', docs_step1_p: 'Lataa GarageAAtoESP32-flash-tool.zip Releases-sivulta, pura se ja kaksoisnapsauta flash.bat.',
    docs_step2_h: 'Asenna Android-sovellus', docs_step2_p: 'Lataa se Google Playsta tai asenna APK Releases-sivulta.',
    docs_step3_h: 'Yhdistä ja mene', docs_step3_p: 'Avaa Garage Opener puhelimessasi — se ohjaa sinut ESP32:n skannaukseen ja salasanan syöttämiseen. Yhdistä sitten puhelin Android Autoon ja napauta Avaa autotalli.',
    docs_stuck_h: 'Vieläkö jumissa?', docs_stuck_p: 'Avaa issue GitHubissa ja liitä sarjamonitorin tulostus ja levytyyppi.',
    docs_issue: 'Avaa issue',
  },

  pt: {
    label: 'Português', flag: 'PT',
    hero_chip:    'Sem cloud · Sem subscrição · Sem Wi-Fi',
    hero_h1_a:   'A tua garagem abre-se',
    hero_h1_b:   'antes de parares o carro',
    hero_sub:    'Um toque no ecrã do Android Auto — ou diz',
    hero_sub_em: '"Abre a garagem"',
    hero_sub2:   '— e o ESP32 carrega no comando por ti. Ou simplesmente chega: a geofence abre antes de parares. Sem internet. Sem cloud.',
    hero_cta_play: 'Transferir no Google Play',
    hero_cta_github: 'GitHub',
    ticker: ['Android Auto','ESP32 BLE','Sem internet','Auth HMAC-SHA256','Código aberto','Comandos de voz','Abertura por geofence','Sem soldar','Mosaico de acesso rápido','Firmware baixo consumo'],
    who_chip: 'A situação',
    who_h2_a: 'Garagem partilhada.',
    who_h2_b: 'Comando emprestado.',
    who_p1: 'Usas uma garagem comunitária — um bloco de apartamentos, um espaço alugado — onde não podes tocar no motor ou no controlador do portão. Só tens um comando.',
    who_p2: 'Isto dá-te uma forma mãos-livres de abrir a porta a partir do ecrã Android Auto do teu carro. Sem internet. Sem cloud. Sem hub. O ESP32 carrega no botão do comando por ti.',
    stat_ble: '< 1s', stat_ble_l: 'ida e volta BLE típica',
    stat_lang: '8', stat_lang_l: 'Idiomas suportados',
    stat_cloud: '0', stat_cloud_l: 'Dependências cloud',
    stat_trigger: '3', stat_trigger_l: 'Opções de acionamento (incl. sem soldar)',
    feat_chip: 'Bem feito',
    feat_h2_a: 'Tudo o que esperarias.',
    feat_h2_b: 'Nada que não precises.',
    feat1_h: 'Autenticação HMAC-SHA256', feat1_p: 'A tua palavra-passe nunca viaja pelo ar — apenas o hash de um nonce aleatório. Os ataques de repetição são impossíveis por design.',
    feat2_h: 'Android Auto nativo',      feat2_p: 'Construído na Car App Library. Um único botão no ecrã do carro — seguro de olhar do banco do condutor.',
    feat3_h: 'Comandos de voz',          feat3_p: '"Abre a garagem" pelo microfone do carro — o Google encaminha a intenção diretamente para o comando BLE. Em todos os 8 idiomas.',
    feat4_h: 'Abertura automática por geofence', feat4_p: 'Define um raio no mapa em Definições. Ao entrar na zona com Android Auto ligado, o portão abre-se sozinho — sem tocar em nada. Uma notificação confirma cada disparo automático.',
    feat5_h: 'Indicador de presença',    feat5_p: 'Uma verificação BLE em segundo plano de baixo consumo mostra um ponto verde quando o abridor está ao alcance.',
    feat6_h: 'Opção para inquilinos',    feat6_p: 'Não podes modificar o comando? A opção B usa um relé para comutar a alimentação da pilha — discos de fita de cobre no compartimento, sem soldar dentro. Totalmente reversível.',
    how_chip: 'O fluxo',
    how_h2_a: 'Do telefone ao portão',
    how_h2_b: 'em menos de um segundo',
    step1_h: 'Toques no ecrã (ou dizes)',                   step1_p: 'Um botão no teu ecrã Android Auto. Ou pergunta ao Google — "Abre a garagem" funciona em 8 idiomas.',
    step2_h: 'O telefone envia um comando HMAC via BLE',    step2_p: 'A app aplica hash a um nonce aleatório com a tua palavra-passe e envia apenas o hash — a palavra-passe nunca sai do telefone.',
    step3_h: 'O ESP32 verifica e atua',                     step3_p: 'O ESP32 na tua garagem valida o HMAC, aciona o relé e envia uma notificação de estado.',
    step4_h: 'O ecrã confirma — o portão abre-se',         step4_p: 'O anel fica verde no sucesso, mostra "Tentar novamente" em caso de falha. Ida e volta típica: menos de 1 segundo.',
    hw_chip: 'Escolhe a tua configuração',
    hw_h2_a: 'Três opções de acionamento.',
    hw_h2_b: 'Um firmware.',
    hw_sub: 'O firmware ESP32 gere as três — escolhe a que se adequa à tua situação.',
    hw_a_h: 'Módulo de relé', hw_a_badge: 'Recomendado',
    hw_a_p: 'Um pequeno módulo de relé curto-circuita os pads do botão do comando. Fácil para iniciantes — o driver integrado trata de tudo. Use uma bobine de 3V (ex. Songle SRD-03VDC-SL-C) diretamente no rail 3,3V do ESP32.',
    hw_a_t1: '~$1–2', hw_a_t2: 'Soldadura necessária',
    hw_b_h: 'Relé + comutação de alimentação', hw_b_badge: 'Para arrendatários',
    hw_b_p: 'Mesmo módulo de relé que A, mas comuta o rail de bateria do comando. Discos de fita de cobre na ranhura da pilha — sem soldadura dentro do comando. Totalmente reversível.',
    hw_b_t1: 'Sem modificação', hw_b_t2: '~$1–2',
    hw_c_h: 'Pulso capacitivo', hw_c_badge: 'Sem soldadura',
    hw_c_p: 'O ESP32 envia um pulso de carga para um pedaço de fita de cobre no botão capacitivo de um Fingerbot Plus — que pressiona mecanicamente o comando.',
    hw_c_t1: 'Para inquilinos', hw_c_t2: '+0,5–1s de latência',
    deploy_chip: 'Implementação',
    deploy_h2_a: 'No carro ou na',
    deploy_h2_b: 'entrada da garagem',
    deploy_car_h: 'No carro', deploy_car_badge: 'Melhor para uso individual',
    deploy_car_p: 'ESP32 e comando viajam no carro, alimentados pela porta USB. O BLE funciona sempre porque tudo está no mesmo veículo.',
    deploy_car_li1: 'Dispara automaticamente ao chegar ao portão',
    deploy_car_li2: 'Carrega quando o carro está ligado',
    deploy_car_li3: 'Nada a instalar ou manter na garagem',
    deploy_ga_h: 'Na garagem', deploy_ga_badge: 'Multi-utilizador',
    deploy_ga_p: 'O ESP32 fica perto do portão, alimentado por tomada, powerbank ou solar. Quem tiver a palavra-passe pode abrir o portão do seu próprio telefone.',
    deploy_ga_li1: 'Suporta vários utilizadores e veículos',
    deploy_ga_li2: 'Deep sleep entre verificações (~12 meses com powerbank)',
    deploy_ga_li3: 'Alcance BLE 10–30 m através de paredes — planeia a colocação',
    sec_h2: 'A tua palavra-passe', sec_h2_b: 'nunca sai do telefone',
    sec_p: 'Cada ligação gera um nonce aleatório no ESP32. O telefone aplica HMAC-SHA256 ao nonce com a tua palavra-passe e envia apenas o hash. O ESP32 verifica-o localmente. Um pacote capturado não tem valor.',
    sec_link: 'Ler o modelo de segurança',
    cta_h2: 'Pronto para abrir mãos-livres?',
    cta_p: 'Transfere a app Android e a ferramenta de flash — operacional num só passo.',
    cta_play: 'Transferir no Google Play',
    cta_docs: 'Ler a documentação',
    nav_back: '← Voltar ao início',
    nav_features: 'Funcionalidades',
    nav_how:      'Como funciona',
    nav_hardware: 'Hardware',
    nav_docs:     'Documentação',
    docs_title: 'Guia de configuração e referência',
    docs_sub: 'Tudo o que precisas para flashar o ESP32 e partir com um portão que abre sozinho.',
    docs_qs_h: 'Início rápido',
    docs_qs_p: 'Três passos desde a caixa até um abridor a funcionar.',
    docs_step1_h: 'Flashar o firmware', docs_step1_p: 'Transfere GarageAAtoESP32-flash-tool.zip da página Releases, extrai e faz duplo clique em flash.bat.',
    docs_step2_h: 'Instalar a app Android', docs_step2_p: 'Transfere-a do Google Play ou instala o APK da página Releases.',
    docs_step3_h: 'Emparelhar e partir', docs_step3_p: 'Abre o Garage Opener no teu telefone — vai guiar-te na leitura do ESP32 e introdução da palavra-passe. Liga depois o telefone ao Android Auto e toca em Abrir garagem.',
    docs_stuck_h: 'Ainda com problemas?', docs_stuck_p: 'Abre uma issue no GitHub incluindo o output do monitor série e o tipo de placa.',
    docs_issue: 'Abrir uma issue',
  },
};

// ── Locale detection & persistence ──────────────────────────────────────────

function detectLocale() {
  const saved = localStorage.getItem('go-locale');
  if (saved && LOCALES[saved]) return saved;
  const browser = (navigator.language || 'en').toLowerCase();
  if (browser.startsWith('es')) return 'es';
  if (browser.startsWith('fr')) return 'fr';
  if (browser.startsWith('de')) return 'de';
  if (browser.startsWith('ca')) return 'ca';
  if (browser.startsWith('it')) return 'it';
  if (browser.startsWith('fi')) return 'fi';
  if (browser.startsWith('pt')) return 'pt';
  return 'en';
}

function saveLocale(code) {
  localStorage.setItem('go-locale', code);
}

// ── DOM helpers ──────────────────────────────────────────────────────────────

// Replace text on any element with data-i18n="key"
function applyTranslations(t) {
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.getAttribute('data-i18n');
    if (t[key] !== undefined) el.textContent = t[key];
  });
  // HTML content (safe — all values come from our own strings object)
  document.querySelectorAll('[data-i18n-html]').forEach(el => {
    const key = el.getAttribute('data-i18n-html');
    if (t[key] !== undefined) el.innerHTML = t[key];
  });
  // Update <html lang> attribute
  const entry = Object.entries(LOCALES).find(([, v]) => v === t);
  if (entry) document.documentElement.lang = entry[0];
}

// ── Language switcher UI ─────────────────────────────────────────────────────

function buildSwitcher(currentCode, onChange) {
  const wrap = document.createElement('div');
  wrap.id = 'lang-switcher';
  wrap.style.cssText = `
    position: fixed; bottom: 24px; right: 24px; z-index: 200;
    display: flex; flex-direction: column; align-items: flex-end; gap: 8px;
  `;

  // Flyout menu (hidden by default)
  const menu = document.createElement('div');
  menu.style.cssText = `
    background: #14181C;
    border: 1px solid rgba(255,255,255,0.10);
    border-radius: 14px;
    padding: 6px;
    display: none;
    flex-direction: column;
    gap: 2px;
    box-shadow: 0 20px 60px rgba(0,0,0,0.6);
    min-width: 160px;
  `;

  Object.entries(LOCALES).forEach(([code, loc]) => {
    const btn = document.createElement('button');
    btn.innerHTML = `<span style="font-size:10px;font-weight:700;letter-spacing:0.5px;background:rgba(255,255,255,0.08);border-radius:4px;padding:2px 5px;margin-right:6px;color:#8A939C">${loc.flag}</span>${loc.label}`;
    btn.style.cssText = `
      background: ${code === currentCode ? 'rgba(42,212,163,0.12)' : 'transparent'};
      color: ${code === currentCode ? '#2AD4A3' : '#8A939C'};
      border: none; cursor: pointer;
      border-radius: 9px; padding: 9px 14px;
      font-size: 13.5px; font-weight: 600;
      text-align: left; width: 100%;
      transition: background 0.15s, color 0.15s;
      font-family: 'Inter', system-ui, sans-serif;
      letter-spacing: 0.1px;
    `;
    btn.onmouseover = () => { if (code !== currentCode) { btn.style.background = 'rgba(255,255,255,0.05)'; btn.style.color = '#F3F5F7'; } };
    btn.onmouseout  = () => { if (code !== currentCode) { btn.style.background = 'transparent'; btn.style.color = '#8A939C'; } };
    btn.onclick = () => {
      menu.style.display = 'none';
      onChange(code);
    };
    menu.appendChild(btn);
  });

  // Toggle button
  const toggle = document.createElement('button');
  toggle.id = 'lang-toggle';
  const curr = LOCALES[currentCode];
  toggle.innerHTML = `<span style="font-size:10px;font-weight:700;letter-spacing:0.5px;background:rgba(255,255,255,0.08);border-radius:4px;padding:2px 5px;color:#8A939C">${curr.flag}</span>&nbsp;${curr.label}`;
  toggle.style.cssText = `
    background: #14181C;
    border: 1px solid rgba(255,255,255,0.10);
    color: #F3F5F7;
    border-radius: 999px;
    padding: 9px 16px;
    font-size: 13px; font-weight: 600;
    cursor: pointer;
    display: flex; align-items: center; gap: 6px;
    box-shadow: 0 4px 20px rgba(0,0,0,0.4);
    transition: border-color 0.2s;
    font-family: 'Inter', system-ui, sans-serif;
  `;
  toggle.onmouseover = () => toggle.style.borderColor = 'rgba(42,212,163,0.5)';
  toggle.onmouseout  = () => toggle.style.borderColor = 'rgba(255,255,255,0.10)';
  toggle.onclick = (e) => {
    e.stopPropagation();
    const open = menu.style.display === 'flex';
    menu.style.display = open ? 'none' : 'flex';
  };
  document.addEventListener('click', () => { menu.style.display = 'none'; });

  wrap.appendChild(menu);
  wrap.appendChild(toggle);
  document.body.appendChild(wrap);
}

// ── Bootstrap ────────────────────────────────────────────────────────────────

function initI18n() {
  let currentCode = detectLocale();

  function apply(code) {
    currentCode = code;
    saveLocale(code);
    applyTranslations(LOCALES[code]);
    // Update ticker if present
    const track = document.getElementById('ticker-track');
    if (track && LOCALES[code].ticker) {
      const items = LOCALES[code].ticker;
      track.innerHTML = [...items, ...items].map((item, i) =>
        i % 2 === 0
          ? `<span style="white-space:nowrap;color:#5A6169;font-size:13px;font-weight:500;padding:0 40px">${item}</span>`
          : `<span style="white-space:nowrap;color:#5A6169;font-size:13px;font-weight:500;padding:0 40px">${item}</span>`
      ).join('<span style="white-space:nowrap;color:#2AD4A3;font-size:13px;padding:0 10px">·</span>');
    }
    // Update switcher toggle label
    const toggle = document.getElementById('lang-toggle');
    if (toggle) {
      const loc = LOCALES[code];
      toggle.innerHTML = `<span style="font-size:10px;font-weight:700;letter-spacing:0.5px;background:rgba(255,255,255,0.08);border-radius:4px;padding:2px 5px;color:#8A939C">${loc.flag}</span>&nbsp;${loc.label}`;
    }
  }

  buildSwitcher(currentCode, apply);
  apply(currentCode);
}

document.addEventListener('DOMContentLoaded', initI18n);