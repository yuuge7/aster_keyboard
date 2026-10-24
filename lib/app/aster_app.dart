import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/keyboard_bridge.dart';

const asterBlue = Color(0xFF25569D);
const asterInk = Color(0xFF1A273B);

class AsterApp extends StatelessWidget {
  const AsterApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Aster Keyboard',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      fontFamily: 'Roboto',
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: asterBlue),
      scaffoldBackgroundColor: const Color(0xFFF7F9FC),
      textTheme: const TextTheme(
        headlineLarge: TextStyle(fontSize: 38, height: 1.12, fontWeight: FontWeight.w700, color: asterInk, letterSpacing: -1.3),
        titleLarge: TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: asterInk),
        bodyLarge: TextStyle(fontSize: 16, height: 1.5, color: Color(0xFF53647C)),
      ),
      filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(
        backgroundColor: asterBlue, minimumSize: const Size(0, 52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      )),
      inputDecorationTheme: InputDecorationTheme(
        filled: true, fillColor: Colors.white,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: Color(0xFFD5DEEB))),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: Color(0xFFD5DEEB))),
      ),
    ),
    home: const KeyboardHome(),
  );
}

class KeyboardHome extends StatefulWidget {
  const KeyboardHome({super.key});
  @override
  State<KeyboardHome> createState() => _KeyboardHomeState();
}

class _KeyboardHomeState extends State<KeyboardHome> with WidgetsBindingObserver {
  final bridge = KeyboardBridge();
  KeyboardStatus status = const KeyboardStatus();
  int page = 0;
  bool loading = true;
  String? error;
  @override
  void initState() { super.initState(); WidgetsBinding.instance.addObserver(this); refresh(); }
  @override
  void dispose() { WidgetsBinding.instance.removeObserver(this); super.dispose(); }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) { refresh(); }
  }
  Future<void> refresh() async {
    try {
      final next = await bridge.status();
      if (mounted) { setState(() { status = next; loading = false; error = null; }); }
    } on PlatformException {
      if (mounted) { setState(() { loading = false; error = 'Could not check Android keyboard status. Tap refresh to try again.'; }); }
    } on MissingPluginException {
      if (mounted) { setState(() { loading = false; error = 'Keyboard activation is available on the Android app.'; }); }
    }
  }
  Future<void> action(Future<void> Function() callback) async {
    try { await callback(); }
    on PlatformException catch (_) { showMessage('Android could not open this screen. Try again from system Settings.'); }
    on MissingPluginException catch (_) { showMessage('This action requires the Android app.'); }
  }
  void showMessage(String text) {
    if (mounted) { ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text))); }
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      backgroundColor: const Color(0xFFF7F9FC), surfaceTintColor: Colors.transparent,
      title: const Row(children: [KeyboardMark(size: 30), SizedBox(width: 10), Text('aster', style: TextStyle(fontWeight: FontWeight.w700, letterSpacing: -0.6))]),
      actions: [IconButton(onPressed: refresh, tooltip: 'Refresh keyboard status', icon: const Icon(Icons.refresh_rounded)), const SizedBox(width: 8)],
    ),
    body: SafeArea(child: Center(child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 600),
      child: IndexedStack(index: page, children: [setup(), playground(), privacy()]),
    ))),
    bottomNavigationBar: NavigationBar(
      selectedIndex: page,
      onDestinationSelected: (value) { FocusManager.instance.primaryFocus?.unfocus(); setState(() { page = value; }); },
      destinations: const [
        NavigationDestination(icon: Icon(Icons.keyboard_outlined), selectedIcon: Icon(Icons.keyboard), label: 'Setup'),
        NavigationDestination(icon: Icon(Icons.edit_note_outlined), selectedIcon: Icon(Icons.edit_note), label: 'Try it'),
        NavigationDestination(icon: Icon(Icons.shield_outlined), selectedIcon: Icon(Icons.shield), label: 'Privacy'),
      ],
    ),
  );
  Widget setup() => ListView(padding: const EdgeInsets.fromLTRB(24, 20, 24, 32), children: [
    const Text('YOUR WORDS. YOUR SPACE.', style: TextStyle(color: asterBlue, fontSize: 11, letterSpacing: 1.8, fontWeight: FontWeight.w700)),
    const SizedBox(height: 14),
    Text(status.selected ? 'Make yourself\nat home.' : 'A quieter place\nto type.', style: Theme.of(context).textTheme.headlineLarge),
    const SizedBox(height: 16),
    const Text('A simple English keyboard that keeps your words on your device.', style: TextStyle(fontSize: 16, height: 1.5, color: Color(0xFF53647C))),
    const SizedBox(height: 26),
    Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(color: const Color(0xFFE7EEF9), borderRadius: BorderRadius.circular(18)),
      child: Row(children: [
        Icon(status.selected ? Icons.check_circle_rounded : Icons.keyboard_alt_outlined, color: asterBlue, size: 28),
        const SizedBox(width: 14),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(loading ? 'Checking your keyboard…' : status.selected ? 'Aster is ready' : status.enabled ? 'One more step' : 'Let’s get you set up', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: asterInk)),
          const SizedBox(height: 4),
          Text(status.selected ? 'Open any app and start typing.' : 'Two steps in Android Settings.', style: const TextStyle(color: Color(0xFF53647C))),
        ])),
      ]),
    ),
    if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error!, style: const TextStyle(color: Color(0xFF9C352A)))),
    if (status.selected) Padding(
      padding: const EdgeInsets.only(top: 16),
      child: FilledButton.icon(onPressed: () => setState(() { page = 1; }), icon: const Icon(Icons.arrow_forward_rounded), label: const Text('Try your keyboard')),
    ),
    const SizedBox(height: 26),
    activationStep('1', 'Enable Aster', 'Turn on Aster Keyboard in Android’s keyboard list.', status.enabled, 'Open keyboard settings', () => action(bridge.enable)),
    const Padding(padding: EdgeInsets.symmetric(vertical: 18), child: Divider(height: 1)),
    activationStep('2', 'Choose your keyboard', 'Select Aster as your current keyboard.', status.selected, 'Choose Aster', status.enabled ? () => action(bridge.choose) : null),
    const SizedBox(height: 24),
    const SizedBox(height: 18),
    const Row(children: [Icon(Icons.wifi_off_rounded, size: 17, color: Color(0xFF53647C)), SizedBox(width: 8), Expanded(child: Text('Works offline. No account. No ads.', style: TextStyle(color: Color(0xFF53647C), fontSize: 12)))]),
  ]);
  Widget activationStep(String number, String title, String subtitle, bool done, String label, VoidCallback? onPressed) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Container(width: 32, height: 32, alignment: Alignment.center,
        decoration: BoxDecoration(color: done ? const Color(0xFFDCEBE3) : const Color(0xFFE6ECF4), shape: BoxShape.circle),
        child: done ? const Icon(Icons.check, size: 18, color: Color(0xFF2A684B)) : Text(number, style: const TextStyle(fontWeight: FontWeight.w700, color: asterBlue))),
      const SizedBox(width: 14),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 17, color: asterInk)),
        const SizedBox(height: 5), Text(subtitle, style: const TextStyle(height: 1.4, color: Color(0xFF53647C))),
        const SizedBox(height: 12),
        OutlinedButton(onPressed: onPressed, child: Text(done ? 'Open again' : label)),
      ])),
    ],
  );
  Widget playground() => ListView(padding: const EdgeInsets.all(24), children: [
    Text('Find your rhythm.', style: Theme.of(context).textTheme.headlineLarge),
    const SizedBox(height: 12),
    const Text('Tap a field to try Aster. These notes stay on this screen and are never saved.', style: TextStyle(height: 1.5, color: Color(0xFF53647C))),
    if (!status.selected) Padding(padding: const EdgeInsets.only(top: 12), child: OutlinedButton.icon(onPressed: () => action(bridge.choose), icon: const Icon(Icons.keyboard), label: const Text('Choose Aster first'))),
    const SizedBox(height: 22),
    const TextField(key: Key('messageField'), minLines: 3, maxLines: 5, keyboardType: TextInputType.multiline, textCapitalization: TextCapitalization.sentences, decoration: InputDecoration(labelText: 'A little space to write', alignLabelWithHint: true, hintText: 'The first thing I want to say…')),
    const SizedBox(height: 18),
    TextField(key: const Key('searchField'), textInputAction: TextInputAction.search, decoration: const InputDecoration(labelText: 'Search', prefixIcon: Icon(Icons.search)), onSubmitted: (_) => showMessage('Search action received.')),
    const SizedBox(height: 18),
    const TextField(key: Key('emailField'), keyboardType: TextInputType.emailAddress, textInputAction: TextInputAction.next, decoration: InputDecoration(labelText: 'Email address')),
    const SizedBox(height: 18),
    const TextField(key: Key('urlField'), keyboardType: TextInputType.url, textInputAction: TextInputAction.go, decoration: InputDecoration(labelText: 'Website')),
    const SizedBox(height: 18),
    const TextField(key: Key('passwordField'), obscureText: true, enableSuggestions: false, autocorrect: false, decoration: InputDecoration(labelText: 'Test password', helperText: 'Use a made-up password here.')),
    const SizedBox(height: 18),
    const TextField(key: Key('numberField'), keyboardType: TextInputType.numberWithOptions(decimal: true, signed: true), decoration: InputDecoration(labelText: 'Number')),
    const SizedBox(height: 22),
    OutlinedButton.icon(onPressed: () => action(bridge.nativeTests), icon: const Icon(Icons.open_in_new_rounded), label: const Text('Open native Android test fields')),
    const SizedBox(height: 16),
    const Text('Keyboard shortcuts', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
    const SizedBox(height: 8),
    const Text('Double-tap Shift for caps lock. Hold Delete to keep deleting. Hold a top-row letter for its number. Use the arrows to move the cursor; hold to select. Tap ◎ to change keyboards.', style: TextStyle(height: 1.6, color: Color(0xFF53647C))),
  ]);
  Widget privacy() => ListView(padding: const EdgeInsets.all(24), children: [
    const Icon(Icons.shield_outlined, size: 48, color: asterBlue),
    const SizedBox(height: 22),
    Text('Your words\nstay with you.', style: Theme.of(context).textTheme.headlineLarge),
    const SizedBox(height: 26),
    privacyItem(Icons.wifi_off, 'Offline by design', 'The release app has no Internet permission. Typed text is sent only to the field you are editing.'),
    privacyItem(Icons.lock_outline, 'Careful with sensitive fields', 'Passwords are not requested from the editor, logged, stored, or used for learning. Aster shows a private-field label when Android identifies a sensitive field.'),
    privacyItem(Icons.content_paste_off_outlined, 'No clipboard collection', 'This version does not read or save your clipboard. There are no ads, accounts, analytics, or typing history.'),
    privacyItem(Icons.info_outline, 'About Android’s warning', 'Android warns that keyboards can read what you type. This warning applies to every third-party keyboard. Only enable keyboards you trust.'),
    const Divider(height: 36),
    Text('Aster Keyboard · 0.1.0', style: Theme.of(context).textTheme.titleMedium),
    const SizedBox(height: 8),
    const Text('Phase 1 · English QWERTY\nSettings, additional languages, suggestions, emoji and personal dictionaries are planned for later phases.', style: TextStyle(height: 1.6, color: Color(0xFF53647C))),
    const SizedBox(height: 16),
    TextButton(onPressed: () => showLicensePage(context: context, applicationName: 'Aster Keyboard', applicationVersion: '0.1.0'), child: const Text('Open-source licenses')),
  ]);
  Widget privacyItem(IconData icon, String title, String body) => Padding(
    padding: const EdgeInsets.only(bottom: 24),
    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Icon(icon, color: asterBlue, size: 22), const SizedBox(width: 14),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: asterInk)),
        const SizedBox(height: 6), Text(body, style: const TextStyle(height: 1.5, color: Color(0xFF53647C))),
      ])),
    ]),
  );
}

class KeyboardMark extends StatelessWidget {
  const KeyboardMark({super.key, required this.size});
  final double size;
  @override
  Widget build(BuildContext context) => Semantics(label: 'Aster', child: Container(
    width: size, height: size,
    decoration: BoxDecoration(color: asterBlue, borderRadius: BorderRadius.circular(size * .25)),
    child: Icon(Icons.keyboard_alt_rounded, color: Colors.white, size: size * .7),
  ));
}
