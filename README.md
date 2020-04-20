# jXNB
Java classes for reading, modifying, and writing XNB files

Terraria, Axiom Verge, and many other XNA / .NET / Mono / Unity games
use XNB to store textures, maps, models, audio, etc.


XNA is an extensible container format. This means that new types of
serialized data might simply be unknown to this toolkit. XNB also
has structures to serialize cyclic structures and arbitrary classes
with generics. Some of this data just won't map well to java because
it refers to some arbitrary library specific to XNA Game Studio. This
library will work best when the deserialized data is a tree structure
of simple types, like json, or a set of simple data buffers, like
`SoundEffect`s or `Texture2D`s.


In particular this library should allow one to go directly from Java2D
BufferedImage to an XNB texture file and back in straightforward ways.
