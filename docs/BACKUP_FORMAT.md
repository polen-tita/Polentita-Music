# Formato de backup

Versión actual: `1`.

El archivo ZIP contiene una única entrada UTF-8:

```text
polentita-backup.json
```

Campos raíz:

- `version`: entero obligatorio.
- `createdAt`: epoch en milisegundos.
- `audioIncluded`: siempre `false`.
- `songs`, `albums`, `playlists`, `playlistSongs`, `history`: arreglos.
- `preferences`: tema, color dinámico, restauración de cola, desconexión de auriculares y política Wi-Fi.

Las IDs originales se conservan para mantener claves foráneas y orden de playlists. La importación valida la versión antes de modificar Room y restaura las tablas en orden referencial. El URI de la carpeta y su permiso no se importan.
