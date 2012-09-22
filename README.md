play-terminal
=============

playFramework based html5 terminal

Public API:

console.Console

console.PlayOutputStream



Internals:

To send to the Akka Actor from the HTML5 terminal:
 * look in terminal.js and search for "actorSocket".
 * actorSocket is passed to the terminal class in Terminal.js from the Terminal constructor in terminal.scala.html
 
Printing of the actor message into the HTML5 terminal happens in terminal.scala.html
 * look for "actorSocket" and "term.output("

