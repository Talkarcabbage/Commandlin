
# Commandlin

Commandlin is a small, simple, and convenient command management library for Kotlin that manages the handling and delegation of command implementations. Example use cases are things such as commandline argument handling, terminal command feedback, bot commands, and etc.

It has functionality for a user-controllable permission system, generic types, allows for optionally defining a developer-controlled "source" object, and 

## Usage/Examples

```kotlin
fun main() {
    val cmd = commandlin<List<String>, Nothing?, Author?> {
        command("test") {
            func { args, source ->
                println("We have ${args.size} arguments from $source")
            }
        }
    }

    val someArguments = listOf("Arg one", "Arg two")
    val someOtherArguments = listOf("")
    val author = Author("Steve")

    cmd.process("test", someArguments, null, author)
    cmd.process("test", someOtherArguments, null, null)

}

class Author(var name: String)


```

