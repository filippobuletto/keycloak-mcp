# Contributing

Contributions are welcome. By contributing, you agree that your contribution is licensed under the
[European Union Public Licence v. 1.2](LICENSE), the licence of this project.

## Developer Certificate of Origin

Every commit must carry a `Signed-off-by` line. With it, you certify that you wrote the change or otherwise have the
right to submit it under the project's licence, as stated in the Developer Certificate of Origin below. Pull requests
with unsigned commits are not merged.

Sign off a commit with `-s`:

```shell
git commit -s -m "fix: describe the change"
```

This adds a trailer with the name and email from your Git configuration:

```text
Signed-off-by: Your Name <you@example.com>
```

Use your real name. To sign off commits you already made on your branch, rebase them:

```shell
git rebase --signoff main
```

The full text of the certificate, from <https://developercertificate.org/>:

```text
Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.


Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```

## Making a change

Run the tests and the formatter check before you open a pull request:

```shell
./mvnw verify
```

Commit messages follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

Opening an issue or pull request creates no obligation for the maintainer to review, merge, or answer it.
