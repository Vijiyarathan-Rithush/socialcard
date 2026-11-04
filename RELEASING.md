# Releasing SocialCard

This is a prepared release configuration, not evidence that the component has been published.
The prepared first release is `io.github.vijiyarathan-rithush:socialcard:0.1.0` under MIT.
The MIT license has been selected. Confirm the exact verified namespace before publishing or tagging a release.
The Java package names remain `com.rvi` and do not need to match the Maven group ID.

## Preview without credentials

Use JDK 25 and Maven:

```bash
mvn -Prelease -Dgpg.skip=true clean verify
```

This runs tests and generates the library, sources and Javadoc JARs. It does not upload
anything. The preview artifacts are unsigned and must not be submitted as a final release.
The API reference is generated without adding Java comments or Javadoc to the source.
Usage examples remain in README.md.

## Publisher setup

1. Sign in to https://central.sonatype.com with the GitHub account that owns the repository.
2. Confirm the exact namespace displayed in the portal. GitHub sign-in commonly provisions
   `io.github.<username>` automatically; update the POM if its case or spelling differs.
3. Generate a Central Portal user token. Use its username and password, not a GitHub token.
4. Create or select your own OpenPGP signing key and make its public key discoverable through
   a key server supported by Central. Keep a secure backup of the private key.
5. Provide `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `MAVEN_GPG_KEY` and
   `MAVEN_GPG_PASSPHRASE` in your local environment or encrypted CI secrets.
   `MAVEN_GPG_KEY` contains the exported OpenPGP private key used by the Maven BC signer.
   Never commit private keys, passwords or populated settings files.

Create a private Maven settings file outside the repository with this server entry:

```xml
<settings>
    <servers>
        <server>
            <id>central</id>
            <username>${env.CENTRAL_USERNAME}</username>
            <password>${env.CENTRAL_PASSWORD}</password>
        </server>
    </servers>
</settings>
```

## Build and sign

```bash
mvn -Prelease clean verify
```

Expected artifacts include `socialcard-0.1.0.jar`, `socialcard-0.1.0-sources.jar`,
`socialcard-0.1.0-javadoc.jar` and their OpenPGP signatures. The deployment process signs
the POM as well. Applications provide their own SLF4J logging implementation; Logback is
only a test dependency of the library.

## Upload and publish

After verifying the namespace and committing the reviewed release source:

```bash
mvn -Prelease deploy
```

The publishing plugin generates the checksums and uploads a signed deployment to Central.
It waits for validation but does not publish automatically. Review the validated deployment
in the portal and choose Publish to make it available on Maven Central.
A published coordinate cannot be overwritten, so use a new version for later changes.

After the portal reports publication, verify the POM and all three JARs at:

https://repo.maven.apache.org/maven2/io/github/vijiyarathan-rithush/socialcard/0.1.0/

Then tag the reviewed source commit as `v0.1.0` and create the corresponding GitHub release.
Do not label the component as available on Maven Central before publication is verified.

## References

- https://central.sonatype.org/publish/requirements/
- https://central.sonatype.org/register/namespace/
- https://central.sonatype.org/publish/publish-portal-maven/
- https://central.sonatype.org/publish/requirements/gpg/
- https://maven.apache.org/plugins/maven-gpg-plugin/sign-mojo.html
