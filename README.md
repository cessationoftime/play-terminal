play-terminal
=============

playFramework based html5 terminal

terminal page is at:
localhost:9000/terminal

Public API:

console.Console.systemInstall()  //install to system.out, system.err, start playframework/akka
console.Console.out.println() //print to HTML5 terminal
console.Console.out.print()   //print to HTML5 terminal
console.Console.html_out() //print unfiltered HTML strings to terminal
console.Console.systemUninstall() //uninstall from system.out, system,err, stop playframework/akka

Internals:

To send to the Akka Actor from the HTML5 terminal:
 * look in terminal.js and search for "actorSocket.send".
 * actorSocket is passed to the terminal class in Terminal.js from the Terminal constructor in terminal.scala.html
 
Printing of the actor message into the HTML5 terminal happens in terminal.scala.html
 * look for "actorSocket" and "term.output("

