# MagpieServer
An http version of the AP Magpie Chatbot with a backend Java server and an HTML frontend.

The Magpie Server will be created by extending a simple Http server that is designed to
serve static files from a public folder. The user can access the server and it will 
automatically serve an `index.html` file if there is one in the `public` folder along
with all of the resources it needs (images, css, etc.).

With a mixture of JavaScript code on the frontend and a new REST endpoint on the server,
this project provides an exploration of client/server architecture.

## Explore:
Look through the `.java` files and the `public` folder to see what files we will be starting with.

## Plan:
- [ ] Create a new Java Class that will handle chat requests
- [ ] Add code to the UI to send chat requests
- [ ] Test that messages are getting from the client to the server
- [ ] Add code to the server to interact with Magpie
- [ ] Add code to the server to send Magpie's responses
- [ ] Make sure that the client is receiving Magpie's responses
- [ ] Add code to display chat messages to the client
