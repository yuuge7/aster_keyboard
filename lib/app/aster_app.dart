import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../services/keyboard_bridge.dart';
import '../services/keyboard_settings.dart';
import 'settings_screen.dart';

const asterBlue = Color(0xFF25569D);

class AsterApp extends StatefulWidget {
  const AsterApp({super.key});
  @override
  State<AsterApp> createState() => _AsterAppState();
}

class _AsterAppState extends State<AsterApp> {
  final bridge = KeyboardBridge();
  var settings = const KeyboardSettings();
  Future<void> apply(KeyboardSettings next) async {
    if (settings.wallpaper.isNotEmpty &&
        next['wallpaperRevision'] != settings['wallpaperRevision']) {
      await FileImage(File(settings.wallpaper)).evict();
    }
    if (mounted) setState(() => settings = next);
  }

  Future<void> reload() async => apply(await bridge.settings());
  Future<void> save(Map<String, Object?> patch) async =>
      apply(await bridge.update(patch));
  ThemeData theme(Brightness brightness) {
    final scheme = ColorScheme.fromSeed(
      seedColor: asterBlue,
      brightness: brightness,
    );
    final dark = brightness == Brightness.dark;
    return ThemeData(
      useMaterial3: true,
      fontFamily: 'Roboto',
      colorScheme: scheme,
      scaffoldBackgroundColor: dark
          ? const Color(0xFF101826)
          : const Color(0xFFF7F9FC),
      appBarTheme: AppBarTheme(
        backgroundColor: dark
            ? const Color(0xFF101826)
            : const Color(0xFFF7F9FC),
        surfaceTintColor: Colors.transparent,
      ),
      textTheme: TextTheme(
        headlineLarge: TextStyle(
          fontSize: 36,
          height: 1.12,
          fontWeight: FontWeight.w700,
          color: scheme.onSurface,
          letterSpacing: -.9,
        ),
        titleLarge: TextStyle(
          fontSize: 22,
          fontWeight: FontWeight.w600,
          color: scheme.onSurface,
        ),
        bodyLarge: TextStyle(
          fontSize: 16,
          height: 1.5,
          color: scheme.onSurfaceVariant,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(0, 50),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: scheme.surfaceContainerLow,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Aster Keyboard',
    debugShowCheckedModeBanner: false,
    theme: theme(Brightness.light),
    darkTheme: theme(Brightness.dark),
    themeMode: switch (settings.theme) {
      'dark' => ThemeMode.dark,
      'light' => ThemeMode.light,
      _ => ThemeMode.system,
    },
    home: KeyboardHome(
      settings: settings,
      bridge: bridge,
      reload: reload,
      save: save,
      chooseWallpaper: () async => apply(await bridge.chooseWallpaper()),
      removeWallpaper: () async => apply(await bridge.removeWallpaper()),
    ),
  );
}

class KeyboardHome extends StatefulWidget {
  const KeyboardHome({
    super.key,
    required this.settings,
    required this.bridge,
    required this.reload,
    required this.save,
    required this.chooseWallpaper,
    required this.removeWallpaper,
  });
  final KeyboardSettings settings;
  final KeyboardBridge bridge;
  final Future<void> Function() reload, chooseWallpaper, removeWallpaper;
  final Future<void> Function(Map<String, Object?>) save;
  @override
  State<KeyboardHome> createState() => _KeyboardHomeState();
}

class _KeyboardHomeState extends State<KeyboardHome>
    with WidgetsBindingObserver {
  KeyboardStatus status = const KeyboardStatus();
  int page = 0;
  bool loading = true, busy = false;
  String? error;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.bridge.onSettingsRequested(() {
      FocusManager.instance.primaryFocus?.unfocus();
      if (mounted) setState(() => page = 2);
    });
    refresh();
  }

  @override
  void dispose() {
    widget.bridge.onSettingsRequested(null);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) refresh();
  }

  Future<void> refresh() async {
    try {
      final next = await widget.bridge.status();
      await widget.reload();
      final openSettings = await widget.bridge.consumeSettingsRequest();
      if (openSettings) FocusManager.instance.primaryFocus?.unfocus();
      if (mounted) {
        setState(() {
          status = next;
          loading = false;
          error = null;
          if (openSettings) page = 2;
        });
      }
    } on PlatformException {
      if (mounted) {
        setState(() {
          loading = false;
          error = 'Could not check Android keyboard status or settings. Tap refresh to try again.';
        });
      }
    } on MissingPluginException {
      if (mounted) {
        setState(() {
          loading = false;
          error = 'Keyboard activation and settings require the Android app.';
        });
      }
    }
  }

  Future<void> action(
    Future<void> Function() callback, {
    bool saving = false,
  }) async {
    if (saving && busy) return;
    if (saving) setState(() => busy = true);
    try {
      await callback();
    } on PlatformException catch (exception) {
      message(
        exception.message ??
            'Android could not complete this action. Try again.',
      );
    } on MissingPluginException {
      message('This action requires the Android app.');
    } finally {
      if (mounted && saving) setState(() => busy = false);
    }
  }

  void message(String text) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Row(
        children: [
          KeyboardMark(size: 30),
          SizedBox(width: 10),
          Text(
            'aster',
            style: TextStyle(fontWeight: FontWeight.w700, letterSpacing: -.6),
          ),
        ],
      ),
      actions: [
        IconButton(
          onPressed: refresh,
          tooltip: 'Refresh keyboard status',
          icon: const Icon(Icons.refresh_rounded),
        ),
        const SizedBox(width: 8),
      ],
    ),
    body: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 600),
          child: IndexedStack(
            index: page,
            children: [
              setup(),
              playground(),
              SettingsScreen(
                settings: widget.settings,
                busy: busy,
                onSave: (patch) =>
                    action(() => widget.save(patch), saving: true),
                onChooseWallpaper: () =>
                    action(widget.chooseWallpaper, saving: true),
                onRemoveWallpaper: () =>
                    action(widget.removeWallpaper, saving: true),
              ),
              privacy(),
            ],
          ),
        ),
      ),
    ),
    bottomNavigationBar: NavigationBar(
      selectedIndex: page,
      onDestinationSelected: (value) {
        FocusManager.instance.primaryFocus?.unfocus();
        setState(() => page = value);
      },
      destinations: const [
        NavigationDestination(
          icon: Icon(Icons.keyboard_outlined),
          selectedIcon: Icon(Icons.keyboard),
          label: 'Setup',
        ),
        NavigationDestination(
          icon: Icon(Icons.edit_note_outlined),
          selectedIcon: Icon(Icons.edit_note),
          label: 'Try it',
        ),
        NavigationDestination(
          icon: Icon(Icons.tune_outlined),
          selectedIcon: Icon(Icons.tune),
          label: 'Settings',
        ),
        NavigationDestination(
          icon: Icon(Icons.shield_outlined),
          selectedIcon: Icon(Icons.shield),
          label: 'Privacy',
        ),
      ],
    ),
  );
  Widget setup() {
    final scheme = Theme.of(context).colorScheme;
    return ListView(
      padding: const EdgeInsets.fromLTRB(24, 20, 24, 32),
      children: [
        Text(
          'YOUR WORDS. YOUR SPACE.',
          style: TextStyle(
            color: scheme.primary,
            fontSize: 11,
            letterSpacing: 1.8,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 14),
        Text(
          status.selected
              ? 'Make yourself\nat home.'
              : 'A quieter place\nto type.',
          style: Theme.of(context).textTheme.headlineLarge,
        ),
        const SizedBox(height: 16),
        const Text(
          'English and Romanian. Your style, your words, all on your device.',
          style: TextStyle(fontSize: 16, height: 1.5),
        ),
        const SizedBox(height: 26),
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: scheme.primaryContainer,
            borderRadius: BorderRadius.circular(18),
          ),
          child: Row(
            children: [
              Icon(
                status.selected
                    ? Icons.check_circle_rounded
                    : Icons.keyboard_alt_outlined,
                color: scheme.onPrimaryContainer,
                size: 28,
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      loading
                          ? 'Checking your keyboard…'
                          : status.selected
                          ? 'Aster is ready'
                          : status.enabled
                          ? 'One more step'
                          : 'Let’s get you set up',
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 16,
                        color: scheme.onPrimaryContainer,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      status.selected
                          ? 'Open any app and start typing.'
                          : 'Two steps in Android Settings.',
                      style: TextStyle(color: scheme.onPrimaryContainer),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        if (error != null)
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Text(error!, style: TextStyle(color: scheme.error)),
          ),
        if (status.selected)
          Padding(
            padding: const EdgeInsets.only(top: 16),
            child: FilledButton.icon(
              onPressed: () => setState(() => page = 1),
              icon: const Icon(Icons.arrow_forward_rounded),
              label: const Text('Try your keyboard'),
            ),
          ),
        const SizedBox(height: 26),
        activationStep(
          '1',
          'Enable Aster',
          'Turn on Aster Keyboard in Android’s keyboard list.',
          status.enabled,
          'Open keyboard settings',
          () => action(widget.bridge.enable),
        ),
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 18),
          child: Divider(height: 1),
        ),
        activationStep(
          '2',
          'Choose your keyboard',
          'Select Aster as your current keyboard.',
          status.selected,
          'Choose Aster',
          status.enabled ? () => action(widget.bridge.choose) : null,
        ),
        const SizedBox(height: 24),
        OutlinedButton.icon(
          onPressed: () => setState(() => page = 2),
          icon: const Icon(Icons.palette_outlined),
          label: const Text('Personalize your keyboard'),
        ),
        const SizedBox(height: 24),
        const Row(
          children: [
            Icon(Icons.wifi_off_rounded, size: 17),
            SizedBox(width: 8),
            Expanded(
              child: Text(
                'Works offline. No account. No ads.',
                style: TextStyle(fontSize: 12),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget activationStep(
    String number,
    String title,
    String subtitle,
    bool done,
    String label,
    VoidCallback? onPressed,
  ) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      CircleAvatar(
        radius: 16,
        backgroundColor: Theme.of(context).colorScheme.secondaryContainer,
        child: done ? const Icon(Icons.check, size: 18) : Text(number),
      ),
      const SizedBox(width: 14),
      Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 17),
            ),
            const SizedBox(height: 5),
            Text(subtitle, style: const TextStyle(height: 1.4)),
            const SizedBox(height: 12),
            OutlinedButton(
              onPressed: onPressed,
              child: Text(done ? 'Open again' : label),
            ),
          ],
        ),
      ),
    ],
  );
  Widget playground() => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      Text(
        'Find your rhythm.',
        style: Theme.of(context).textTheme.headlineLarge,
      ),
      const SizedBox(height: 12),
      const Text(
        'Try “teh ” in English or “multumesc ” in Romanian. These notes are never saved.',
        style: TextStyle(height: 1.5),
      ),
      if (!status.selected)
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: OutlinedButton.icon(
            onPressed: () => action(widget.bridge.choose),
            icon: const Icon(Icons.keyboard),
            label: const Text('Choose Aster first'),
          ),
        ),
      const SizedBox(height: 22),
      const TextField(
        key: Key('messageField'),
        minLines: 3,
        maxLines: 5,
        keyboardType: TextInputType.multiline,
        textCapitalization: TextCapitalization.sentences,
        decoration: InputDecoration(
          labelText: 'A little space to write',
          alignLabelWithHint: true,
          hintText: 'Bună! Hello!',
        ),
      ),
      const SizedBox(height: 18),
      TextField(
        key: const Key('searchField'),
        textInputAction: TextInputAction.search,
        decoration: const InputDecoration(
          labelText: 'Search',
          prefixIcon: Icon(Icons.search),
        ),
        onSubmitted: (_) => message('Search action received.'),
      ),
      const SizedBox(height: 18),
      const TextField(
        key: Key('emailField'),
        keyboardType: TextInputType.emailAddress,
        textInputAction: TextInputAction.next,
        decoration: InputDecoration(labelText: 'Email address'),
      ),
      const SizedBox(height: 18),
      const TextField(
        key: Key('urlField'),
        keyboardType: TextInputType.url,
        textInputAction: TextInputAction.go,
        decoration: InputDecoration(labelText: 'Website'),
      ),
      const SizedBox(height: 18),
      const TextField(
        key: Key('passwordField'),
        obscureText: true,
        enableSuggestions: false,
        autocorrect: false,
        decoration: InputDecoration(
          labelText: 'Test password',
          helperText: 'Use a made-up password here.',
        ),
      ),
      const SizedBox(height: 18),
      const TextField(
        key: Key('numberField'),
        keyboardType: TextInputType.numberWithOptions(
          decimal: true,
          signed: true,
        ),
        decoration: InputDecoration(labelText: 'Number'),
      ),
      const SizedBox(height: 22),
      OutlinedButton.icon(
        onPressed: () => action(widget.bridge.nativeTests),
        icon: const Icon(Icons.open_in_new_rounded),
        label: const Text('Open native Android test fields'),
      ),
      const SizedBox(height: 22),
      const Text(
        'At your fingertips',
        style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
      ),
      const SizedBox(height: 8),
      const Text(
        'Tap the language name or hold Space to switch English/Romanian. Hold letters for accents and numbers. Tap ☺ for emoji; hold supported hand emoji for skin tones. Backspace immediately after an autocorrection to undo it. Double-tap Shift for caps lock.',
        style: TextStyle(height: 1.6),
      ),
    ],
  );
  Widget privacy() => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      Icon(
        Icons.shield_outlined,
        size: 48,
        color: Theme.of(context).colorScheme.primary,
      ),
      const SizedBox(height: 22),
      Text(
        'Your words\nstay with you.',
        style: Theme.of(context).textTheme.headlineLarge,
      ),
      const SizedBox(height: 26),
      privacyItem(
        Icons.wifi_off,
        'Offline by design',
        'The release app has no Internet permission. English and Romanian dictionaries are bundled on your device. Typed words are never sent to a server.',
      ),
      privacyItem(
        Icons.lock_outline,
        'Careful with sensitive fields',
        'Passwords are not read, corrected, logged, or learned. Suggestions and automatic punctuation are disabled in private fields, email addresses, URLs, and number pads.',
      ),
      privacyItem(
        Icons.photo_outlined,
        'Your preferences, on your device',
        'Settings, your chosen wallpaper, explicitly added dictionary words, and recent emoji are saved privately. Wallpapers are copied without their location metadata. Typed conversations are never saved.',
      ),
      privacyItem(
        Icons.content_paste_off_outlined,
        'No clipboard collection',
        'There is no clipboard history, account, advertising, analytics, or automatic learning from your typing.',
      ),
      privacyItem(
        Icons.info_outline,
        'About Android’s warning',
        'Android warns that keyboards can read what you type. This applies to every third-party keyboard. Sensitive-field detection depends on the receiving app.',
      ),
      const Divider(height: 36),
      Text(
        'Aster Keyboard${widget.settings.version.isEmpty ? '' : ' · ${widget.settings.version}'}',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      const SizedBox(height: 8),
      const Text(
        'English · Română\nOffline typing, made personal.',
        style: TextStyle(height: 1.6),
      ),
      const SizedBox(height: 16),
      TextButton(
        onPressed: () => showLicensePage(
          context: context,
          applicationName: 'Aster Keyboard',
          applicationVersion: widget.settings.version.isEmpty
              ? null
              : widget.settings.version,
        ),
        child: const Text('Open-source licenses'),
      ),
    ],
  );
  Widget privacyItem(IconData icon, String title, String body) => Padding(
    padding: const EdgeInsets.only(bottom: 24),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: Theme.of(context).colorScheme.primary, size: 22),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              Text(body, style: const TextStyle(height: 1.5)),
            ],
          ),
        ),
      ],
    ),
  );
}

class KeyboardMark extends StatelessWidget {
  const KeyboardMark({super.key, required this.size});
  final double size;
  @override
  Widget build(BuildContext context) => Semantics(
    label: 'Aster',
    child: Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: asterBlue,
        borderRadius: BorderRadius.circular(size * .25),
      ),
      child: Icon(
        Icons.keyboard_alt_rounded,
        color: Colors.white,
        size: size * .7,
      ),
    ),
  );
}
