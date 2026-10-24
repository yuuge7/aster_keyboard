class KeyboardSettings {
  const KeyboardSettings([this.values = const {}]);
  final Map<String, Object?> values;
  static const defaults = <String, Object?>{
    'theme': 'system',
    'language': 'en',
    'englishEnabled': true,
    'romanianEnabled': true,
    'autoCorrect': true,
    'suggestions': true,
    'autoCapitalization': true,
    'punctuationSpace': true,
    'doubleSpacePeriod': true,
    'restoreDiacritics': true,
    'numberRow': false,
    'haptics': false,
    'sound': false,
    'keyHeight': 52,
    'wallpaperDim': .35,
    'personalWordsEn': '',
    'personalWordsRo': '',
  };
  Object? operator [](String key) => values[key] ?? defaults[key];
  bool enabled(String key) => this[key] == true;
  String text(String key) => this[key] as String? ?? '';
  double number(String key) => (this[key] as num?)?.toDouble() ?? 0;
  String get theme => text('theme');
  String get language => text('language');
  String get wallpaper => text('wallpaperPath');
  String get version => text('appVersion');
}
