'use strict';

var config = require('./config.json');


const express = require('express');
const auth = require('basic-auth-connect');
const parser = require('body-parser');

const app = express();

app.use(auth(config.basic_auth.username, config.basic_auth.password));

app.use(parser.urlencoded({ extended: true }));
app.use(parser.json());

app.get('/ping', (req, res) => {
  res
    .status(200)
    .send('Alive')
    .end();
});

app.post('/webhook', (req, res) => {
  var query = req.body.queryResult.parameters['query'];
  res
    .status(200)
    .send('{ "fulfillmentText": "' + query + 'ですか？" }')
    .end();
});

// Start the server
const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`App listening on port ${PORT}`);
  console.log('Press Ctrl+C to quit.');
});

module.exports = app;
