import 'package:flutter/material.dart';

import '../services/keyboard_settings.dart';

class PersonalDictionaryDialog extends StatefulWidget {
  const PersonalDictionaryDialog({super.key, required this.settings});
  final KeyboardSettings settings;
  @override
  State<PersonalDictionaryDialog> createState() =>
      _PersonalDictionaryDialogState();
}

class _PersonalDictionaryDialogState extends State<PersonalDictionaryDialog> {
  late final english = TextEditingController(
    text: widget.settings.text('personalWordsEn'),
  );
  late final romanian = TextEditingController(
    text: widget.settings.text('personalWordsRo'),
  );
  late String language = widget.settings.language;
  String? error;
  @override
  void dispose() {
    english.dispose();
    romanian.dispose();
    super.dispose();
  }

  void save() {
    for (final controller in [english, romanian]) {
      final words = controller.text
          .split('\n')
          .map((word) => word.trim())
          .where((word) => word.isNotEmpty)
          .toSet();
      if (words.length > 500 ||
          words.any(
            (word) =>
                word.length > 40 ||
                !RegExp(r"^[\p{L}'’-]+$", unicode: true).hasMatch(word),
          )) {
        setState(
          () => error = 'Use up to 500 words per language, one per line. Only letters, apostrophes, and hyphens are allowed.',
        );
        return;
      }
    }
    Navigator.pop(context, {
      'personalWordsEn': english.text,
      'personalWordsRo': romanian.text,
    });
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Personal dictionary'),
    content: SizedBox(
      width: 360,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'en', label: Text('English')),
                ButtonSegment(value: 'ro', label: Text('Română')),
              ],
              selected: {language},
              onSelectionChanged: (values) =>
                  setState(() => language = values.first),
            ),
            const SizedBox(height: 18),
            TextField(
              key: ValueKey('dictionary-$language'),
              controller: language == 'en' ? english : romanian,
              minLines: 5,
              maxLines: 8,
              autocorrect: false,
              enableSuggestions: false,
              keyboardType: TextInputType.multiline,
              decoration: const InputDecoration(
                labelText: 'One word per line',
                alignLabelWithHint: true,
                helperText: 'Up to 500 words for each language.',
              ),
            ),
            if (error != null)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(
                  error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancel'),
      ),
      FilledButton(onPressed: save, child: const Text('Save words')),
    ],
  );
}
