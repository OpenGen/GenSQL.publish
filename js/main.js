const React = require('react');
const ReactDOM = require('react-dom');
const components = require('@inferenceql/inferenceql.react');

const execute = function(s) {
  return fetch('/api/query', {
    method: 'post',
    body: JSON.stringify({ query: s }),
    cache: 'no-cache',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    }
  }).then((response) => {
    if (response.status === 200) {
      return response.json();
    } else if (response.status === 400) {
      return Promise.reject(response.json());
    } else if (response.status === 500) {
      alert("Internal query execution error");
      throw response;
    } else {
      alert("Unhandled HTTP status code from server");
      throw response
    }
  })
};

module.exports = {
  React: require('react'),
  ReactDOM: require('react-dom'),
  inferenceql: { react: require('@inferenceql/inferenceql.react') },
  execute: execute
};
