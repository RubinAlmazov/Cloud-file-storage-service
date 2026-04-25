claude --resume 6e7dd819-2bd7-4e57-bc5a-df58f47c7b0a 

# Баг в MyTinyParser — над-чтение в `extractContentDisposition`

Баг живёт в соседнем проекте `/home/rubin/IdeaProjects/MyTinyParser`, но проявляется здесь — при POST `/resource?path=...` через Postman с form-data и файлом падает `ArrayIndexOutOfBoundsException` из `ResourceService.uploadResource` → `MyTinyParserApplication.parseAll`.

## Симптом

```
java.lang.ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0
  at org.me.mytinyparser.utils.BoundedInputStream.read(BoundedInputStream.java:27)
  at java.io.InputStream.transferTo(InputStream.java:796)
  at org.me.mytinyparser.MyTinyParserApplication.parseAll(MyTinyParserApplication.java:30)
```

## Причина

В `ParserService.extractContentDisposition` цикл читает `inputStream` чанками по 8192 байта в `outputStream`. Когда в аккумулированном буфере находится `\r\n\r\n` (конец заголовков части), код делает:

```java
} else {
    outputStream.reset();   // ← ПРОБЛЕМА
}
```

Для маленького файла весь multipart-запрос целиком помещается в один `read()`. После этого `outputStream` содержит:

```
--boundary\r\n
Content-Disposition: form-data; name="object"; filename="test.txt"\r\n
Content-Type: text/plain\r\n\r\n
Hello world\r\n
--boundary--\r\n
```

После нахождения `\r\n\r\n` вызов `outputStream.reset()` **выбрасывает содержимое файла и закрывающий boundary**. Исходный `InputStream` в этот момент уже вычитан до EOF.

Дальше `parseAll` вызывает `extractResourceContent()`:

```java
public BoundedInputStream extractResourceContent() throws IOException {
    byte[] boundary = ("\r\n--" + rawBoundary).getBytes();
    return new BoundedInputStream(inputStream, boundary);   // inputStream уже пуст
}
```

В конструкторе `BoundedInputStream`:

```java
this.buffer = source.readNBytes(boundary.length);   // возвращает пустой массив, т.к. EOF
```

Первый вызов `read()` на строке 27 делает `buffer[0]` на пустом массиве → краш.

## Что нужно сделать (план фикса)

### 1. `ParserService.java` — сохранить хвост после `\r\n\r\n`

Добавить импорты:
```java
import java.io.ByteArrayInputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
```

Добавить поле:
```java
private byte[] leftover = new byte[0];
```

В `extractContentDisposition`, ветка где `state == FOUND_DISPOSITION` перед `outputStream.reset()` — сохранить всё, что после `\r\n\r\n`:

```java
} else {
    byte[] all = outputStream.toByteArray();
    int sepEnd = containsSubArray(all, new byte[] {'\r', '\n', '\r', '\n'});
    if (sepEnd != -1 && sepEnd < all.length) {
        leftover = Arrays.copyOfRange(all, sepEnd, all.length);
    }
    outputStream.reset();
}
```

(`containsSubArray` уже возвращает индекс ПОСЛЕ найденной подстроки — как раз то, что нужно.)

Переписать `extractResourceContent` так, чтобы leftover подклеивался в начало исходного стрима:

```java
public BoundedInputStream extractResourceContent() throws IOException {
    byte[] boundary = ("\r\n--" + rawBoundary).getBytes();
    InputStream source = leftover.length > 0
            ? new SequenceInputStream(new ByteArrayInputStream(leftover), inputStream)
            : inputStream;
    leftover = new byte[0];
    return new BoundedInputStream(source, boundary);
}
```

### 2. `BoundedInputStream.java` — защита от короткого буфера

Если стрим уже в EOF к моменту создания `BoundedInputStream` (или в нём меньше байтов, чем длина boundary) — не падать, а корректно отдать то, что есть, и вернуть `-1`. В конструкторе:

```java
public BoundedInputStream(InputStream stream, byte[] boundary) throws IOException {
    this.source = stream;
    this.boundary = boundary;
    this.buffer = source.readNBytes(boundary.length);
    if (this.buffer.length < boundary.length) {
        this.tailPos = 0;   // сразу в режим «слить буфер и вернуть -1»
    }
}
```

Это чисто подстраховка — после фикса #1 ситуация пустого буфера не должна возникать, но защищаться стоит.

### 3. После правки — пересобрать JAR

```
cd /home/rubin/IdeaProjects/MyTinyParser
mvn clean install
```

Убедиться, что в `~/.m2/repository/org/me/MyTinyParser/0.0.1-SNAPSHOT/` свежий `.jar`. Перезапустить `CloudFileStorage` (IntelliJ может кешировать — если что, `File → Invalidate Caches`).

## Ранее исправленный баг (для контекста)

Был ещё один баг в том же файле — `parseContentDisposition` крашился на пустой строке (`Range [19, -1) out of bounds for length 0`), когда парсер пытался прочитать следующую часть после последней. Уже поправлен: в `extractContentDisposition` перед возвратом проверяется, что `state == REACHED_END` и в буфере нет `Content-Disposition:` — в этом случае возвращаем `null`.

## Отдельно — мелочи в основном проекте

Эти вещи не вызывают падения, но подсвечены при разборе и требуют отдельного прохода:

1. **`SecurityConfig.java:36`** — матчер `/resource*` не покрывает `/resource/download`, `/resource/rename` и прочие подпути. Нужен `/resource/**` (двойная звёздочка пересекает `/`). И путь `/error` стоит тоже добавить в `permitAll`, чтобы ошибки в контроллерах возвращали нормальный 500, а не маскировались под 401.

2. **`ResourceService.uploadResource` (`ResourceService.java:274`)** — не соответствует спеке upload'а:
   - Кладёт все файлы в `.object(path)` — перезаписывают друг друга. Нужно `path + part.contentDisposition().getFilename()`.
   - Использует `part.contentDisposition().getName()` — это имя HTML-поля, не имя файла. Нужен `getFilename()`.
   - Возвращает `200 OK` вместо требуемого `201 Created`.
   - Нет проверки, существует ли папка `path` в хранилище.

3. **`application.properties`** — добавлено `spring.servlet.multipart.enabled=false`, чтобы Spring не читал тело и не мешал `MyTinyParser`. Если когда-нибудь переключение на стандартный `MultipartFile` — флаг убрать.
