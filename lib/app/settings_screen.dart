import 'dart:io';

import 'package:flutter/material.dart';

import '../services/keyboard_settings.dart';
import 'personal_dictionary_dialog.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    super.key,
    required this.settings,
    required this.busy,
    required this.onSave,
    required this.onChooseWallpaper,
    required this.onRemoveWallpaper,
  });
  final KeyboardSettings settings;
  final bool busy;
  final Future<void> Function(Map<String, Object?>) onSave;
  final Future<void> Function() onChooseWallpaper, onRemoveWallpaper;
  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  double? height, dim;
  KeyboardSettings get settings => widget.settings;
  Widget section(String title) => Padding(
    padding: const EdgeInsets.only(top: 28, bottom: 8),
    child: Text(title, style: Theme.of(context).textTheme.titleLarge),
  );
  Widget toggle(
    String key,
    String title,
    String subtitle, {
    bool locked = false,
  }) => SwitchListTile.adaptive(
    key: ValueKey('setting-$key'),
    contentPadding: EdgeInsets.zero,
    title: Text(title),
    subtitle: Text(subtitle),
    value: settings.enabled(key),
    onChanged: widget.busy || locked
        ? null
        : (value) => widget.onSave({key: value}),
  );
  @override
  Widget build(BuildContext context) => ListView(
    key: const PageStorageKey('keyboardSettings'),
    padding: const EdgeInsets.all(24),
    children: [
      Text('Make it yours.', style: Theme.of(context).textTheme.headlineLarge),
      const SizedBox(height: 10),
      const Text(
        'Changes apply to Aster wherever you type.',
        style: TextStyle(height: 1.5),
      ),
      const SizedBox(height: 22),
      KeyboardPreview(settings: settings),
      section('Appearance'),
      const SizedBox(height: 6),
      SegmentedButton<String>(
        segments: const [
          ButtonSegment(value: 'system', label: Text('System')),
          ButtonSegment(value: 'light', label: Text('Light')),
          ButtonSegment(value: 'dark', label: Text('Dark')),
        ],
        selected: {settings.theme},
        onSelectionChanged: widget.busy
            ? null
            : (values) => widget.onSave({'theme': values.first}),
      ),
      const SizedBox(height: 18),
      OutlinedButton.icon(
        key: const Key('chooseWallpaper'),
        onPressed: widget.busy ? null : widget.onChooseWallpaper,
        icon: const Icon(Icons.add_photo_alternate_outlined),
        label: Text(
          settings.wallpaper.isEmpty
              ? 'Choose keyboard wallpaper'
              : 'Change wallpaper',
        ),
      ),
      if (widget.busy)
        const Padding(
          padding: EdgeInsets.only(top: 8),
          child: LinearProgressIndicator(),
        ),
      if (settings.wallpaper.isNotEmpty) ...[
        TextButton.icon(
          onPressed: widget.busy ? null : widget.onRemoveWallpaper,
          icon: const Icon(Icons.delete_outline),
          label: const Text('Remove wallpaper'),
        ),
        const SizedBox(height: 8),
        const Text('Wallpaper dimming'),
        Slider(
          value: dim ?? settings.number('wallpaperDim'),
          min: 0,
          max: .85,
          divisions: 17,
          label: '${((dim ?? settings.number('wallpaperDim')) * 100).round()}%',
          onChanged: widget.busy
              ? null
              : (value) => setState(() => dim = value),
          onChangeEnd: (value) async {
            await widget.onSave({'wallpaperDim': value});
            if (mounted) setState(() => dim = null);
          },
        ),
      ],
      Text(
        'Choose a photo with Android’s picker. A private copy stays on this device.',
        style: Theme.of(context).textTheme.bodySmall,
      ),
      section('Languages'),
      toggle(
        'englishEnabled',
        'English',
        'QWERTY · English dictionary',
        locked: !settings.enabled('romanianEnabled'),
      ),
      toggle(
        'romanianEnabled',
        'Română',
        'QWERTY · ă â î ș ț · Romanian dictionary',
        locked: !settings.enabled('englishEnabled'),
      ),
      const SizedBox(height: 12),
      DropdownButtonFormField<String>(
        key: ValueKey('language-${settings.language}'),
        initialValue: settings.language,
        decoration: const InputDecoration(labelText: 'Current typing language'),
        items: [
          if (settings.enabled('englishEnabled'))
            const DropdownMenuItem(value: 'en', child: Text('English')),
          if (settings.enabled('romanianEnabled'))
            const DropdownMenuItem(value: 'ro', child: Text('Română')),
        ],
        onChanged: widget.busy
            ? null
            : (value) {
                if (value != null) widget.onSave({'language': value});
              },
      ),
      const SizedBox(height: 10),
      const Text(
        'Tap the language name on the keyboard or hold Space to switch. Hold the language name to open Android’s keyboard picker.',
        style: TextStyle(height: 1.5),
      ),
      section('Typing assistance'),
      toggle(
        'suggestions',
        'Word suggestions',
        'Show completions and spelling suggestions above the keys.',
      ),
      toggle(
        'autoCorrect',
        'Autocorrect',
        'Correct likely mistakes when you type a space or punctuation. Backspace to undo.',
      ),
      if (settings.enabled('romanianEnabled'))
        toggle(
          'restoreDiacritics',
          'Romanian diacritics',
          'Restore common forms such as “multumesc” → “mulțumesc”.',
        ),
      toggle(
        'autoCapitalization',
        'Automatic capitalization',
        'Start sentences with a capital letter. Shift always remains available.',
      ),
      toggle(
        'punctuationSpace',
        'Space after punctuation',
        'Add a space after a comma, semicolon, colon, question mark, or exclamation mark.',
      ),
      toggle(
        'doubleSpacePeriod',
        'Double-space period',
        'Tap Space twice quickly to insert a period and a space.',
      ),
      ListTile(
        contentPadding: EdgeInsets.zero,
        leading: const Icon(Icons.book_outlined),
        title: const Text('Personal dictionary'),
        subtitle: const Text(
          'Add names or words explicitly. Typing is never learned automatically.',
        ),
        trailing: const Icon(Icons.chevron_right),
        onTap: widget.busy ? null : editDictionary,
      ),
      section('Keys and feedback'),
      toggle(
        'numberRow',
        'Always show number row',
        'Keep 1–0 above the letters.',
      ),
      toggle(
        'haptics',
        'Vibration on keypress',
        'Use Android’s keyboard haptic feedback.',
      ),
      toggle(
        'sound',
        'Sound on keypress',
        'Use Android’s key click sound and system volume.',
      ),
      const SizedBox(height: 16),
      Text(
        'Key height · ${(height ?? settings.number('keyHeight')).round()} dp',
      ),
      Slider(
        value: height ?? settings.number('keyHeight'),
        min: 42,
        max: 66,
        divisions: 12,
        label: '${(height ?? settings.number('keyHeight')).round()} dp',
        onChanged: widget.busy
            ? null
            : (value) => setState(() => height = value),
        onChangeEnd: (value) async {
          await widget.onSave({'keyHeight': value.round()});
          if (mounted) setState(() => height = null);
        },
      ),
      const SizedBox(height: 10),
      const Text(
        'Suggestions, autocorrect, and automatic spacing stay off in sensitive fields, email addresses, URLs, and number pads.',
        style: TextStyle(height: 1.5),
      ),
      const SizedBox(height: 24),
    ],
  );
  Future<void> editDictionary() async {
    final values = await showDialog<Map<String, Object?>>(
      context: context,
      builder: (_) => PersonalDictionaryDialog(settings: settings),
    );
    if (values != null && mounted) await widget.onSave(values);
  }
}

class KeyboardPreview extends StatelessWidget {
  const KeyboardPreview({super.key, required this.settings});
  final KeyboardSettings settings;
  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final surface = dark ? const Color(0xFF101826) : const Color(0xFFE9EFF6);
    final keyColor = dark ? const Color(0xFF233249) : Colors.white;
    final ink = dark ? const Color(0xFFE6EDF8) : const Color(0xFF1A273B);
    Widget legend(String key) {
      final icon = switch (key) {
        '⇧' => Icons.arrow_upward_rounded,
        '⌫' => Icons.backspace_outlined,
        '☺' => Icons.emoji_emotions_outlined,
        '↵' => Icons.keyboard_return_rounded,
        '⚙' => Icons.settings_outlined,
        _ => null,
      };
      if (icon != null) return Icon(icon, size: 15, color: ink);
      return Text(
        key == 'space'
            ? settings.language == 'ro'
                  ? 'Română'
                  : 'English'
            : key,
        style: TextStyle(color: ink, fontSize: key == 'space' ? 10 : 12),
      );
    }

    Widget row(List<String> keys) => Row(
      children: keys
          .map(
            (key) => Expanded(
              flex: key == 'space' ? 4 : 1,
              child: Container(
                height: 28,
                alignment: Alignment.center,
                margin: const EdgeInsets.all(2),
                decoration: BoxDecoration(
                  color: keyColor.withValues(
                    alpha: settings.wallpaper.isEmpty ? 1 : .85,
                  ),
                  borderRadius: BorderRadius.circular(5),
                ),
                child: legend(key),
              ),
            ),
          )
          .toList(),
    );
    return Semantics(
      label: 'Keyboard appearance preview',
      image: true,
      child: ExcludeSemantics(
        child: ClipRRect(
          borderRadius: BorderRadius.circular(14),
          child: Stack(
            children: [
              Positioned.fill(child: ColoredBox(color: surface)),
              if (settings.wallpaper.isNotEmpty)
                Positioned.fill(
                  child: Image.file(
                    key: ValueKey(settings['wallpaperRevision']),
                    File(settings.wallpaper),
                    fit: BoxFit.cover,
                    errorBuilder: (_, _, _) => ColoredBox(color: surface),
                  ),
                ),
              if (settings.wallpaper.isNotEmpty)
                Positioned.fill(
                  child: ColoredBox(
                    color: Colors.black.withValues(
                      alpha: settings.number('wallpaperDim'),
                    ),
                  ),
                ),
              Padding(
                padding: const EdgeInsets.all(8),
                child: Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 5),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceAround,
                        children: [
                          'aster',
                          settings.language == 'ro' ? 'Bună' : 'Hello',
                          '⚙',
                        ].map((text) => legend(text)).toList(),
                      ),
                    ),
                    if (settings.enabled('numberRow'))
                      row('1234567890'.split('')),
                    if (settings.language == 'ro') row('ăâîșț'.split('')),
                    row('qwertyuiop'.split('')),
                    row('asdfghjkl'.split('')),
                    row(['⇧', ...'zxcvbnm'.split(''), '⌫']),
                    row(['123', '☺', ',', 'space', '.', '↵']),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
