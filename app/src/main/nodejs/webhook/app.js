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

app.post('/webhook', (req, res) => {
  const speech = req.body.queryResult['queryText'];
  const query = req.body.queryResult.parameters['query'];
  
  (async () => {
    var reply;
    var search;
    
    try {
      const tweet_id = await statuses_update(speech);
      const reply_promise = statuses_filter(tweet_id);
      const search_promise = search_tweets(query);
      reply = await reply_promise;
      search = await search_promise;
    } catch (error) {
      console.log(error);
    }
    
    const reply_message = reply || search || '今、手が離せなくて';
    console.log(reply_message);
    res
      .status(200)
      .send('{ "fulfillmentText": "' + reply_message + '" }')
      .end();
  })();
});

// Start the server
const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`App listening on port ${PORT}`);
  console.log('Press Ctrl+C to quit.');
});

module.exports = app;

async function statuses_update(speech) {
  return new Promise((resolve, reject) => {
    const params = { status: '横浜のおじいちゃんがつぶやいています：\n『' + speech + '』\n' };
    client.post('statuses/update', params, (error, tweet, response) => {
      if (!error) {
        resolve(tweet.id_str);
      } else {
        reject(error);
      }
    });
  });
}

async function search_tweets(query) {
  return new Promise((resolve, reject) => {
    const params = { q: 'to:yoohootea #おーい○○ #' + query, lang: 'ja' };
    client.get('search/tweets', params, (error, tweets, response) => {
      if (!error) {
        const count = tweets.statuses.length;
        if (count > 0) {
          const tweet = tweets.statuses[Math.floor(Math.random() * count)];
          resolve(parse_text(tweet.text));
        } else {
          resolve();
        }
      } else {
        reject(error);
      }
    });
  });
}

async function statuses_filter(tweet_id) {
  return new Promise((resolve, reject) => {
    const params = { follow: '1152750195102236672', lang: 'ja' }; // YooHooTeaのuser_id
    client.stream('statuses/filter', params, (stream) => {
      stream.on('data', (event) => {
        if (event.in_reply_to_status_id_str == tweet_id) {
          clearTimeout(timer);
          stream.destroy();
          resolve(parse_text(event.text));
        }
      });
      
      stream.on('error', (error) => {
        clearTimeout(timer);
        stream.destroy();
        reject(error);
      });
      
      const timer = setTimeout(() => { stream.destroy(); resolve(); }, 3000);
    });
  });
}

function parse_text(text) {
  var result = '';
  for (var value of text.split(/[ \n]/)) {
    var ch = value.slice(0, 1);
    if (ch == '@' || ch == '#') {
      continue;
    }
    result += value;
  }
  return result;
}
