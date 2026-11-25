# Material eReader

Удобная читалка книг для Android в стиле Material You.

- EPUB, FB2, FB2.zip, TXT, комиксы CBZ/CBR
- Прокрутка или страницы с анимациями перелистывания, разворот в альбомной ориентации
- Темы «Бумага», светлая, тёмная, OLED-ночь и Material You
- Закладки, оглавление, прогресс с отметками глав, время до конца главы
- Автопоиск книг в выбранных папках, каталоги OPDS
- Автообновление из GitHub Releases

## Сборка

```
./gradlew :app:assembleDebug
```

## Выпуск новой версии

1. Поднять `versionCode` и `versionName` в `app/build.gradle.kts`.
2. Описать изменения в `CHANGELOG-latest.md`.
3. Открыть вкладку Actions → Release → Run workflow (или отправить тег):
   ```
   git tag v0.2.1 && git push origin v0.2.1
   ```
4. GitHub Actions соберёт подписанный APK и опубликует релиз. Установленные приложения предложат обновиться.

### Секреты репозитория (Settings → Secrets and variables → Actions)

| Имя | Что это |
| --- | --- |
| `KEYSTORE_BASE64` | файл ключа `.jks`, закодированный в base64 |
| `KEYSTORE_PASSWORD` | пароль хранилища |
| `KEY_ALIAS` | `materialereader` |
| `KEY_PASSWORD` | пароль ключа |

Все версии должны подписываться одним и тем же ключом, иначе Android не даст обновиться поверх.
