'use strict';

const config = require('./config.json');
const express = require('express');
const auth = require('basic-auth-connect');
const parser = require('body-parser');
const twitter = require('twitter');

const app = express();

app.use(auth(config.basic_auth.username, config.basic_auth.password));
app.use(parser.urlencoded({ extended: true }));
app.use(parser.json());

const client = new twitter({
  consumer_key: config.twitter.consumer_key,
  consumer_secret: config.twitter.consumer_secret,
  access_token_key: config.twitter.access_token_key,
  access_token_secret: config.twitter.access_token_secret
});

app.get('/mediator/ping', (req, res) => {
  res
    .status(200)
    .send('Alive')
    .end();
});

app.post('/mediator/hello', (req, res) => {
  const name = req.body['name'];
  
  res
    .status(200)
    .send('{ message: "Hi, ' + name + '!" }')
    .end();
});

app.post('/mediator/register', (req, res) => {
  const user_id = req.body['id_str'];
  
  (async () => {
    var body;
    try {
      const url = await friendships_create(user_id);
      body = '{ result: true, profile_image_url: "' + url + '" }';
    } catch (error) {
      console.log(error);
      body = '{ result: false, profile_image_url: "" }';
    }
    
    res
      .status(200)
      .send(body)
      .end();
  })();
});

app.post('/mediator/unregister', (req, res) => {
  const user_id = req.body['id_str'];
  
  (async () => {
    var body;
    try {
      const url = await friendships_destroy(user_id);
      body = '{ result: true }';
    } catch (error) {
      console.log(error);
      body = '{ result: false }';
    }
    
    res
      .status(200)
      .send(body)
      .end();
  })();
});

app.post('/mediator/retweet', (req, res) => {
  const status_id = req.body['id_str'];
  
  (async () => {
    var body;
    try {
      const url = await retweet(status_id);
      body = '{ result: true }';
    } catch (error) {
      console.log(error);
      body = '{ result: false }';
    }
    
    res
      .status(200)
      .send(body)
      .end();
  })();
});

app.post('/mediator/review', (req, res) => {
  const text = req.body['text'];
  
  var body;
  if (text.length > 3) {
    body = '{ result: true }';
  } else {
    console.log('text: ' + text);
    body = '{ result: false }';
  }
  
  res
    .status(200)
    .send(body)
    .end();
});

// Start the server
const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`App listening on port ${PORT}`);
  console.log('Press Ctrl+C to quit.');
});

module.exports = app;

async function friendships_create(user_id) {
  return new Promise((resolve, reject) => {
    const params = { user_id: user_id };
    client.post('friendships/create', params, (error, user, response) => {
      if (!error) {
        console.log(user);
        resolve(user.profile_image_url);
      } else {
        reject(error);
      }
    });
  });
}

async function friendships_destroy(user_id) {
  return new Promise((resolve, reject) => {
    const params = { user_id: user_id };
    client.post('friendships/destroy', params, (error, user, response) => {
      if (!error) {
        console.log(user);
        resolve();
      } else {
        reject(error);
      }
    });
  });
}

async function retweet(status_id) {
  return new Promise((resolve, reject) => {
    const params = { id: status_id  };
    client.post('statuses/retweet', params, (error, retweet, response) => {
      if (!error) {
        console.log(retweet);
        resolve();
      } else {
        reject(error);
      }
    });
  });
}
