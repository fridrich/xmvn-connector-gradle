This is free software. You can redistribute and/or modify it under the
terms of Apache License Version 2.0.

XMvn connector for Gradle was written by Mikolaj Izdebski.

Building
--------

XMvn connector for Gradle require Gradle, which is not available in
Maven Central repository.  Therefore the first time you build XMvn
you'll need to download and install required dependencies into Maven
local repository by running the following command:

    mvn -f libs install

After Gradle is available in your local repository you'll be able to
build XMvn connector for Gradle using standard Maven commands, or
import it into IDEs like Eclipse.
