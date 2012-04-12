Flip-It for Musubi
=============

Flip-It is a flipbook creator and viewer for Musubi. Flip-It uses
[SocialKit](http://github.com/mobisocial/socialkit) to create and share flipbooks with friends.

Creating Flipbooks
------------------

Flip-It is a [Musubi](http://mobisocial.stanford.edu/musubi) application. Flip-It installs itself
in the Musubi feed menu by filtering for an intent with action android.intent.action.MAIN and
category musubi.intent.category.MENU.

When creating a flipbook, Flip-It hooks into Android's camera application to let the user quickly
capture a series of photos. Each photo is shared in Musubi as an obj of type "picture". The flipbook
is simply an obj with a series of such pictures as children. The flipbook itself has its own given
type "flipbook".

<pre>
   o [flipbook]
    \___o [picture 1]
    \___o [picture 2]
    \___o [picture 3]
</pre>


Viewing Flipbooks
-----------------
Flip-It registers to view flipbooks by supporting the VIEW intent for content of type
"vnd.musubi.obj/flipbook". When someone clicks to view a flipbook, the FlipbookViewerActivity is
invoked with reference to that flipbook. We then use SocialKit to query Musubi's content provider,
giving us access to the flipbook's pictures.

We use Android's support library to use the loader api, which helps keep the database query from running
on the main thread.